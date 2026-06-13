#!/bin/bash


echo "Starting Node 1 on port 1100 with --init"
java -jar ../out/artifacts/Node/KVStore2PC.jar 0 127.0.0.1 1099 127.0.0.1 1099 --init &


for i in {2..10}; do
  port=$((1000 + i))
  echo "Starting Node $i on port $port"
  java -jar ../out/artifacts/Node/KVStore2PC.jar $i 127.0.0.1 $port 127.0.0.1 1099 &
done

wait