#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

# shellcheck disable=SC1091
. ./.env

bash scripts/gen-compose.sh > docker-compose.yml

NET="${PAXOS_NET:-paxos-key-value-store_paxos_net}"
IMAGE="${PAXOS_IMAGE:-paxos-kvstore:latest}"
EXPECTED_JOINERS=$((CLUSTER_SIZE - 1))

cleanup() {
    docker compose down -v --remove-orphans >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

echo "== Bringing up $CLUSTER_SIZE-node cluster =="
svcs=$(seq 0 $((CLUSTER_SIZE - 1)) | sed 's/^/node/' | tr '\n' ' ')
docker compose up -d --build $svcs

echo "== Waiting for cluster formation ($EXPECTED_JOINERS joiners on node0) =="
joined=0
for _ in $(seq 1 60); do
    joined=$(docker compose logs --no-color node0 2>/dev/null | grep -c "Node In Network" || true)
    [ "$joined" -ge "$EXPECTED_JOINERS" ] && break
    sleep 1
done

if [ "$joined" -lt "$EXPECTED_JOINERS" ]; then
    echo "FAIL: cluster did not form ($joined/$EXPECTED_JOINERS joiners)"
    docker compose logs --no-color node0 || true
    exit 1
fi
echo "Cluster formed with $joined joiners."

TEST_KEY="dockertest_$$"
TEST_VAL="hello-from-dockertest"

echo "== Running PUT/GET round-trip =="
# Client.java loops on while(true) and never exits on stdin EOF, so we keep
# stdin open with `sleep` to allow synchronous RMI responses to flush, then
# kill the JVM with a timer.
LOG=$(mktemp)
{
    printf '{TYPE:PUT,KEY:%s,VALUE:%s}\n' "$TEST_KEY" "$TEST_VAL"
    printf '{TYPE:GET,KEY:%s}\n' "$TEST_KEY"
    sleep 8
} | docker run --rm -i --network "$NET" \
        --entrypoint java "$IMAGE" \
        -cp /app/KVStore2PC.jar manuel.rpckvstore.Client node0 1099 \
        >"$LOG" 2>&1 &
CLIENT_PID=$!

sleep 12
kill "$CLIENT_PID" 2>/dev/null || true
wait "$CLIENT_PID" 2>/dev/null || true

out=$(cat "$LOG")
rm -f "$LOG"

if printf '%s\n' "$out" | grep -E "Recieved Response.*${TEST_VAL}" >/dev/null; then
    echo "PASS: PUT/GET round-trip works in dockerized cluster"
    exit 0
else
    echo "FAIL: GET did not return PUT value '$TEST_VAL'"
    echo "--- captured client output ---"
    printf '%s\n' "$out"
    echo "--- node0 tail ---"
    docker compose logs --no-color --tail=50 node0 || true
    exit 1
fi
