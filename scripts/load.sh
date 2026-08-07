#!/usr/bin/env bash
# Closed-loop throughput benchmark. Brings up a clean cluster (MSG_LOSS=0) and
# runs the single-JVM LoadClient at a sweep of concurrency levels, reporting real
# ops/sec. Unlike paxos_validate.py this reuses one JVM + RMI connection, so the
# number reflects the store, not container/JVM startup.
#
# Env overrides: OPS_PER_THREAD=100  LOAD_SWEEP="8 16 32 64 128"  LOAD_OP=PUT
set -euo pipefail

cd "$(dirname "$0")/.."

# shellcheck disable=SC1091
. ./.env

: "${OPS_PER_THREAD:=100}"
: "${LOAD_SWEEP:=1,2,4,8,16 }"
: "${LOAD_OP:=PUT}"

bash scripts/gen-compose.sh > docker-compose.yml

NET="${PAXOS_NET:-paxos-key-value-store_paxos_net}"
IMAGE="${PAXOS_IMAGE:-paxos-kvstore:latest}"
EXPECTED_JOINERS=$((CLUSTER_SIZE - 1))

cleanup() {
    docker compose down -v --remove-orphans >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

echo "== Bringing up $CLUSTER_SIZE-node cluster (MSG_LOSS=0 for throughput) =="
svcs=$(seq 0 $((CLUSTER_SIZE - 1)) | sed 's/^/node/' | tr '\n' ' ')
MSG_LOSS=0.0 docker compose up -d --build $svcs

echo "== Waiting for cluster formation ($EXPECTED_JOINERS joiners) =="
joined=0
for _ in $(seq 1 60); do
    joined=$(docker compose logs --no-color 2>/dev/null | grep -c "Successfully joined the network." || true)
    [ "$joined" -ge "$EXPECTED_JOINERS" ] && break
    sleep 1
done
if [ "$joined" -lt "$EXPECTED_JOINERS" ]; then
    echo "FAIL: cluster did not form ($joined/$EXPECTED_JOINERS joiners)"
    exit 1
fi
echo "Cluster formed with $joined joiners."

echo
echo "== Throughput sweep (op=$LOAD_OP, opsPerThread=$OPS_PER_THREAD) =="
printf '%-9s  %-18s  %s\n' "threads" "throughput" "latency"
for T in $LOAD_SWEEP; do
    out=$(docker run --rm --network "$NET" \
        --entrypoint java "$IMAGE" \
        -cp /app/KVStore2PC.jar manuel.rpckvstore.LoadClient node0 1099 "$T" "$OPS_PER_THREAD" "$LOAD_OP" 2>&1 || true)
    tput=$(printf '%s\n' "$out" | grep -oE 'THROUGHPUT = [0-9.]+ ops/sec' | sed 's/THROUGHPUT = //' || true)
    lat=$(printf '%s\n' "$out" | grep -oE 'latency ms:.*' || true)
    printf '%-9s  %-18s  %s\n' "$T" "${tput:-ERROR}" "$lat"
done
