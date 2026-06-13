#!/usr/bin/env bash
set -euo pipefail

NET="${PAXOS_NET:-paxos-key-value-store_paxos_net}"
HOST="${PAXOS_HOST:-node0}"
PORT="${PAXOS_PORT:-1099}"
IMAGE="${PAXOS_IMAGE:-paxos-kvstore:latest}"

if ! docker network inspect "$NET" >/dev/null 2>&1; then
    echo "Network '$NET' not found. Bring the cluster up first: make docker-up" >&2
    echo "Or set PAXOS_NET to the correct network name." >&2
    exit 1
fi

FIFO=$(mktemp -u "${TMPDIR:-/tmp}/paxos-client.XXXXXX")
mkfifo "$FIFO"

DOCKER_PID=""
cleanup() {
    [ -n "$DOCKER_PID" ] && kill "$DOCKER_PID" 2>/dev/null || true
    rm -f "$FIFO"
}
trap cleanup EXIT INT TERM

docker run --rm -i --network "$NET" \
    --entrypoint java "$IMAGE" \
    -cp /app/KVStore2PC.jar manuel.rpckvstore.Client "$HOST" "$PORT" < "$FIFO" &
DOCKER_PID=$!

exec 3>"$FIFO"

cat <<EOF
PAXOS KV client. Commands:
  put <key> <value>
  get <key>
  delete <key>
  exit
EOF

while IFS= read -e -r -p "paxos> " line || [ -n "$line" ]; do
    case "$line" in
        put\ *)
            set -- $line
            shift
            key="$1"
            shift
            val="$*"
            printf '{TYPE:PUT,KEY:%s,VALUE:%s}\n' "$key" "$val" >&3
            ;;
        get\ *)
            key="${line#get }"
            printf '{TYPE:GET,KEY:%s}\n' "$key" >&3
            ;;
        delete\ *)
            key="${line#delete }"
            printf '{TYPE:DELETE,KEY:%s}\n' "$key" >&3
            ;;
        exit|quit)
            break
            ;;
        "")
            continue
            ;;
        *)
            printf '%s\n' "$line" >&3
            ;;
    esac
done

exec 3>&-
# Client.java loops on while(true) and never exits on stdin EOF, so we
# give any in-flight RMI calls a moment to flush and then kill the JVM.
sleep 1
kill "$DOCKER_PID" 2>/dev/null || true
wait "$DOCKER_PID" 2>/dev/null || true
