#!/usr/bin/env bash
# Concurrency smoke test: bring up the cluster, then run ThreadedClient, which
# drives one thread per protein (distinct keys) through PUT -> GET -> DELETE and
# verifies each round-trip. ThreadedClient exits non-zero if any protein fails,
# so this script is a real pass/fail gate for concurrent clients.
set -euo pipefail

cd "$(dirname "$0")/.."

# shellcheck disable=SC1091
. ./.env

CLUSTER_NAME="${CLUSTER_NAME:-thread}"
COMPOSE_FILE="docker-compose.${CLUSTER_NAME}.yml"
CLUSTER_NAME="$CLUSTER_NAME" bash scripts/gen-compose.sh > "$COMPOSE_FILE"

NET="${PAXOS_NET:-${CLUSTER_NAME}_paxos_net}"
IMAGE="${PAXOS_IMAGE:-paxos-kvstore:latest}"
DC="docker compose -p $CLUSTER_NAME -f $COMPOSE_FILE"
EXPECTED_JOINERS=$((CLUSTER_SIZE - 1))

cleanup() {
    $DC down -v --remove-orphans >/dev/null 2>&1 || true
    rm -f "$COMPOSE_FILE"
}
trap cleanup EXIT INT TERM

echo "== Bringing up $CLUSTER_SIZE-node cluster '$CLUSTER_NAME' =="
$DC up -d --build

echo "== Waiting for cluster formation ($EXPECTED_JOINERS joiners) =="
joined=0
for _ in $(seq 1 60); do
    joined=$($DC logs --no-color 2>/dev/null | grep -c "Successfully joined the network." || true)
    [ "$joined" -ge "$EXPECTED_JOINERS" ] && break
    sleep 1
done
if [ "$joined" -lt "$EXPECTED_JOINERS" ]; then
    echo "FAIL: cluster did not form ($joined/$EXPECTED_JOINERS joiners)"
    $DC logs --no-color || true
    exit 1
fi
echo "Cluster formed with $joined joiners."

echo "== Running concurrent ThreadedClient (PUT/GET/DELETE per protein) =="
LOG=$(mktemp)
docker run --rm --network "$NET" \
    --entrypoint java "$IMAGE" \
    -cp /app/KVStore2PC.jar manuel.rpckvstore.ThreadedClient node0 1099 \
    >"$LOG" 2>&1 &
CLIENT_PID=$!

waited=0
while kill -0 "$CLIENT_PID" 2>/dev/null; do
    sleep 1
    waited=$((waited + 1))
    if [ "$waited" -ge 120 ]; then
        echo "FAIL: ThreadedClient timed out after ${waited}s"
        kill "$CLIENT_PID" 2>/dev/null || true
        cat "$LOG"; rm -f "$LOG"
        exit 1
    fi
done
CLIENT_RC=0
wait "$CLIENT_PID" 2>/dev/null || CLIENT_RC=$?

out=$(cat "$LOG")
rm -f "$LOG"
printf '%s\n' "$out"

if [ "$CLIENT_RC" -eq 0 ]; then
    echo "PASS: concurrent clients round-tripped every protein"
    exit 0
else
    echo "FAIL: ThreadedClient reported failures (rc=$CLIENT_RC)"
    echo "--- node0 tail ---"
    $DC logs --no-color --tail=50 node0 || true
    exit 1
fi
