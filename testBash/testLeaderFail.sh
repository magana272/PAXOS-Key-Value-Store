#!/bin/bash

pkill -f KVStore2PC.jar

npx kill-port 1099 1101 1102 1103 1104 1105 1106
sleep 1
echo "Starting Node 0 on port 1099 with --init"
java -jar ../out/artifacts/Node/KVStore2PC.jar  0 127.0.0.1 1099 127.0.0.1 1099 --init > node0.log 2>&1 &
echo $! > node0.pid
sleep 1

echo "Starting Node 1 on port 1101"
java -jar ../out/artifacts/Node/KVStore2PC.jar 1 127.0.0.1 1101 127.0.0.1 1099 > node1.log 2>&1 &
echo $! > node1.pid
sleep 1
echo "Starting Node 2 on port 1102"
java -jar ../out/artifacts/Node/KVStore2PC.jar 2 127.0.0.1 1102 127.0.0.1 1099 > node2.log 2>&1 &
echo $! > node2.pid
sleep 1
echo "Starting Node 3 on port 1103"
java -jar ../out/artifacts/Node/KVStore2PC.jar 3 127.0.0.1 1103 127.0.0.1 1099 > node3.log 2>&1 &
echo $! > node3.pid
sleep 1
echo "Starting Node 4 on port 1104"
java -jar ../out/artifacts/Node/KVStore2PC.jar 4 127.0.0.1 1104 127.0.0.1 1099 > node4.log 2>&1 &
echo $! > node4.pid
sleep 1
echo "Starting Node 5 on port 1105"
java -jar ../out/artifacts/Node/KVStore2PC.jar 5 127.0.0.1 1105 127.0.0.1 1099 > node5.log 2>&1 &
echo $! > node5.pid
sleep 1
echo "Starting Node 6 on port 1106"
java -jar ../out/artifacts/Node/KVStore2PC.jar 6 127.0.0.1 1106 127.0.0.1 1099 > node6.log 2>&1 &
echo $! > node6.pid

sleep 5


echo "Running client..."
java -jar ../out/artifacts/Client/KVStore2PC.jar 127.0.0.1 1099 > client1.log 2>&1 &
echo $! > client1.pid

sleep 5
echo "Terminating client..."
kill -9 $(cat client1.pid)
rm client1.pid


echo "Killing Node 2 (highest ID)..."
kill -9 $(cat node6.pid)
rm node6.pid


sleep 2

echo "Re-running client after Node 2 termination..."
java -jar ../out/artifacts/Client/KVStore2PC.jar 127.0.0.1 1099 > client2.log 2>&1 &
echo $! > client2.pid

