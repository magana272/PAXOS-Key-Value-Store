#!/usr/bin/env bash
# Leader-fail smoke test:
#   - Start 7 nodes (0..6, ports 1099 + id).
#   - Run client #1 against node0 for a few seconds, then terminate it.
#   - Kill node6 (the highest-ID node, current leader by the max-ID rule).
#   - Run client #2 and watch leader election kick in.
#
# Per-node logs and PIDs land in a fresh tmp dir; the path is printed at
# startup so you can tail node*.log while the test runs.

set -uo pipefail

cd "$(dirname "$0")/.."

JAR="${JAR:-target/KVStore2PC.jar}"

if [ ! -f "$JAR" ]; then
    echo "JAR not found at $JAR. Run 'make build' first." >&2
    exit 1
fi

RUN_DIR=$(mktemp -d -t paxos-leader-fail.XXXXXX)
echo "Run artifacts: $RUN_DIR"

pkill -f KVStore2PC.jar 2>/dev/null || true
for p in 1099 1100 1101 1102 1103 1104 1105 1106; do
    pid=$(lsof -ti tcp:$p 2>/dev/null || true)
    [ -n "$pid" ] && kill -9 "$pid" 2>/dev/null || true
done
sleep 1

start_node() {
    local id=$1 port=$2 extra=${3:-}
    echo "Starting Node $id on port $port${extra:+ with $extra}"
    # shellcheck disable=SC2086
    java -jar "$JAR" "$id" 127.0.0.1 "$port" 127.0.0.1 1099 $extra \
        > "$RUN_DIR/node$id.log" 2>&1 &
    echo $! > "$RUN_DIR/node$id.pid"
    sleep 1
}

start_node 0 1099 --init
for i in $(seq 1 6); do
    start_node "$i" $((1099 + i))
done

sleep 5

echo "Running client #1..."
java -cp "$JAR" manuel.rpckvstore.Client 127.0.0.1 1099 > "$RUN_DIR/client1.log" 2>&1 &
echo $! > "$RUN_DIR/client1.pid"

sleep 5
echo "Terminating client #1..."
kill -9 "$(cat "$RUN_DIR/client1.pid")" 2>/dev/null || true
rm -f "$RUN_DIR/client1.pid"

echo "Killing Node 6 (highest ID, current leader)..."
kill -9 "$(cat "$RUN_DIR/node6.pid")" 2>/dev/null || true
rm -f "$RUN_DIR/node6.pid"

sleep 2

echo "Re-running client #2 after Node 6 termination..."
java -cp "$JAR" manuel.rpckvstore.Client 127.0.0.1 1099 > "$RUN_DIR/client2.log" 2>&1 &
echo $! > "$RUN_DIR/client2.pid"

echo "Test launched. Artifacts in $RUN_DIR"