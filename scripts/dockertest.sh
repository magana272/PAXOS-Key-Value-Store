set -euo pipefail

cd "$(dirname "$0")/.."

. ./.env

CLUSTER_NAME="${CLUSTER_NAME:-smoke}"
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

TEST_KEY="dockertest_$$"
TEST_VAL="hello-from-dockertest"

echo "== Running PUT/GET round-trip =="
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

if printf '%s\n' "$out" | grep -E "${TEST_KEY}.*: ${TEST_VAL}" >/dev/null; then
    echo "PASS: PUT/GET round-trip works in dockerized cluster"
    exit 0
else
    echo "FAIL: GET did not return PUT value '$TEST_VAL'"
    echo "--- captured client output ---"
    printf '%s\n' "$out"
    echo "--- node0 tail ---"
    $DC logs --no-color --tail=50 node0 || true
    exit 1
fi
