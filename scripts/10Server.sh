#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

JAR="${JAR:-target/KVStore2PC.jar}"

if [ ! -f "$JAR" ]; then
    echo "JAR not found at $JAR. Run 'make build' first." >&2
    exit 1
fi

echo "Starting Node 0 on port 1099 with --init"
java -jar "$JAR" 0 127.0.0.1 1099 127.0.0.1 1099 --init &

for i in $(seq 1 9); do
    port=$((1099 + i))
    echo "Starting Node $i on port $port"
    java -jar "$JAR" "$i" 127.0.0.1 "$port" 127.0.0.1 1099 &
done

wait