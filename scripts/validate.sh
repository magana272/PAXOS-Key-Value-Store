#!/usr/bin/env bash
# Empirical fault-tolerance / latency / throughput validation for the Paxos
# cluster. Each of TRIALS trials runs two conditions:
#
#   loss0 (clean, MSG_LOSS=0):
#     1. Bring up a fresh CLUSTER_SIZE-node cluster.
#     2. Seed the 6-key fixture through node0 (phase=seed).
#     3. Load baseline: replay the fixture THROUGHPUT_REPEATS times (PUT+GET)
#        for a clean latency / throughput sample (phase=load).
#     4. Fixed-N quorum sweep: node0 (query target) and node<N-1> (the max-ID
#        leader) always survive; progressively stop the MIDDLE nodes
#        node<N-2> .. node1. Because the leader never dies, membership is never
#        pruned, the majority threshold stays N/2+1, and PUT/DELETE success
#        collapses exactly when live nodes fall below majority -- a clean step at
#        floor((N-1)/2). At each step run the WRITE path (PUT+GET+DELETE per key,
#        phase=sweep): only writes go through Paxos, so only writes reveal the
#        quorum boundary. GET is leader-routed and would succeed the whole way
#        down, so a GET-only sweep measures nothing about quorum.
#
#   loss1 (MSG_LOSS injected, all nodes alive):
#     5. Bring up a fresh cluster with real per-message loss on the Paxos RPCs.
#     6. Seed, then load: same replay as (3) but under message loss, so the
#        latency / throughput / retry / failure-rate cost of loss is measured.
#
# Retry counts are scraped from node logs (proposer prints "Retrying Paxos").
#
# Output (per trial i, condition L in {0,1}):
#   metrics/trial<i>_loss<L>_seed.csv
#   metrics/trial<i>_loss<L>_load.csv
#   metrics/trial<i>_loss0_sweep_f<k>.csv    (loss0 only; PUT+GET+DELETE per key)
#   metrics/trial<i>_loss<L>_retries.txt
#
# Usage: bash scripts/validate.sh
# Env overrides: TRIALS=5 THROUGHPUT_REPEATS=10 CLUSTER_SIZE=10 MSG_LOSS_RATE=0.1
#                VERIFY_SETTLE_SECS=12 (wait after stopping a node before verify)

set -euo pipefail
cd "$(dirname "$0")/.."

# Capture any overrides from the environment before sourcing .env (which sets
# these unconditionally), then re-apply them so an explicit `VAR=... bash
# scripts/validate.sh` wins over the .env default.
_CLUSTER_SIZE_OVERRIDE="${CLUSTER_SIZE:-}"
_TRIALS_OVERRIDE="${TRIALS:-}"
_THROUGHPUT_REPEATS_OVERRIDE="${THROUGHPUT_REPEATS:-}"
_MSG_LOSS_RATE_OVERRIDE="${MSG_LOSS_RATE:-}"

# shellcheck disable=SC1091
. ./.env

CLUSTER_SIZE="${_CLUSTER_SIZE_OVERRIDE:-${CLUSTER_SIZE:-10}}"
TRIALS="${_TRIALS_OVERRIDE:-${TRIALS:-5}}"
THROUGHPUT_REPEATS="${_THROUGHPUT_REPEATS_OVERRIDE:-${THROUGHPUT_REPEATS:-10}}"
MSG_LOSS_RATE="${_MSG_LOSS_RATE_OVERRIDE:-${MSG_LOSS:-0.1}}"
NET="${PAXOS_NET:-paxos-key-value-store_paxos_net}"
IMAGE="${PAXOS_IMAGE:-paxos-kvstore:latest}"
METRICS_DIR="metrics"
VERIFY_SETTLE_SECS="${VERIFY_SETTLE_SECS:-12}"

mkdir -p "$METRICS_DIR"

run_client() {
    # run_client <host> <port> <phase> <failedCount> <repeats> <nodeId> <outfile>
    docker run --rm --network "$NET" \
        --entrypoint java "$IMAGE" \
        -cp /app/KVStore2PC.jar manuel.rpckvstore.Validate \
        "$1" "$2" "$3" "$4" "$5" "$6" > "$7" 2>&1 || {
            echo "WARN: Validate run failed (phase=$3 failed_count=$4); see $7" >&2
        }
}

wait_for_cluster() {
    local expected_joiners=$((CLUSTER_SIZE - 1))
    local joined=0
    for _ in $(seq 1 60); do
        joined=$(docker compose logs --no-color 2>/dev/null | grep -c "Successfully joined the network." || true)
        [ "$joined" -ge "$expected_joiners" ] && return 0
        sleep 1
    done
    echo "FAIL: cluster did not form ($joined/$expected_joiners joiners)" >&2
    return 1
}

capture_retries() {
    local count
    count=$(docker compose logs --no-color 2>/dev/null | grep -c "Retrying Paxos (attempt" || true)
    echo "$count" > "$1"
}

bring_up() {
    MSG_LOSS="$1" bash scripts/gen-compose.sh > docker-compose.yml
    local svcs
    svcs=$(seq 0 $((CLUSTER_SIZE - 1)) | sed 's/^/node/' | tr '\n' ' ')
    MSG_LOSS="$1" docker compose up -d --build $svcs
    wait_for_cluster
}

tear_down() {
    docker compose down -v --remove-orphans
}

for trial in $(seq 1 "$TRIALS"); do
    echo "=== Trial $trial/$TRIALS [loss0]: $CLUSTER_SIZE-node cluster, MSG_LOSS=0.0 ==="
    bring_up 0.0

    echo "--- seed ---"
    run_client node0 1099 seed 0 1 0 "$METRICS_DIR/trial${trial}_loss0_seed.csv"

    echo "--- load (baseline, ${THROUGHPUT_REPEATS}x fixture, no loss) ---"
    run_client node0 1099 load 0 "$THROUGHPUT_REPEATS" 0 "$METRICS_DIR/trial${trial}_loss0_load.csv"

    # Capture retries for the load phase only, before the sweep starts. The sweep
    # deliberately kills nodes below quorum, which drives Paxos retries that have
    # nothing to do with message loss; counting them here would make the baseline
    # bar dwarf the lossy one and misattribute quorum-loss retries to message loss.
    capture_retries "$METRICS_DIR/trial${trial}_loss0_retries.txt"

    echo "--- fixed-N quorum sweep (node0 + node$((CLUSTER_SIZE - 1)) survive) ---"
    for failed in $(seq 1 $((CLUSTER_SIZE - 2))); do
        victim="node$((CLUSTER_SIZE - 1 - failed))"   # kill middle nodes N-2 .. 1
        echo "Stopping $victim (failed_count=$failed)"
        docker compose stop "$victim" >/dev/null
        sleep "$VERIFY_SETTLE_SECS"  # let Docker drop the stopped node's DNS so dead-peer lookups fail fast
        # sweep = PUT+GET+DELETE per key: the write path runs Paxos, so success
        # collapses when live nodes fall below majority. (A GET-only sweep would
        # succeed the whole way down since reads are leader-routed and the leader
        # is kept alive.)
        run_client node0 1099 sweep "$failed" 1 0 "$METRICS_DIR/trial${trial}_loss0_sweep_f${failed}.csv"
    done

    echo "=== Trial $trial [loss0] done, tearing down ==="
    tear_down

    echo "=== Trial $trial/$TRIALS [loss1]: $CLUSTER_SIZE-node cluster, MSG_LOSS=${MSG_LOSS_RATE} ==="
    bring_up "$MSG_LOSS_RATE"

    echo "--- seed (under loss) ---"
    run_client node0 1099 seed 0 1 0 "$METRICS_DIR/trial${trial}_loss1_seed.csv"

    echo "--- load (under ${MSG_LOSS_RATE} message loss, ${THROUGHPUT_REPEATS}x fixture) ---"
    run_client node0 1099 load 0 "$THROUGHPUT_REPEATS" 0 "$METRICS_DIR/trial${trial}_loss1_load.csv"

    capture_retries "$METRICS_DIR/trial${trial}_loss1_retries.txt"
    echo "=== Trial $trial [loss1] done, tearing down ==="
    tear_down
done

echo "All trials complete. Metrics in $METRICS_DIR/. Now run: python3 scripts/generate_charts.py"
