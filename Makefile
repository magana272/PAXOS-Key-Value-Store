# PAXOS Key-Value Store
#
# Convenience targets for building, testing, and running the cluster.
# Assumes Java 17+ and Maven on PATH.

MVN          ?= mvn
JAR          := target/KVStore2PC.jar
INIT_ID      ?= 0
INIT_HOST    ?= 127.0.0.1
INIT_PORT    ?= 1099
ID           ?= 1
HOST         ?= 127.0.0.1
PORT         ?= 1100
ACCEPT_FAIL  ?= 0.0
PROPOSE_FAIL ?= 0.0

.PHONY: all help build test cucumber clean run-init run-node run-client \
        run-cluster test-leader-fail kill-ports

all: build

help:
	@echo "Targets:"
	@echo "  build             - mvn clean package -> $(JAR)"
	@echo "  test              - mvn test (unit + cucumber)"
	@echo "  cucumber          - run only the cucumber suite"
	@echo "  clean             - mvn clean + remove __MACOSX/ and out/"
	@echo "  run-init          - run initial node on $(INIT_HOST):$(INIT_PORT)"
	@echo "  run-node          - run joining node (ID=$(ID) PORT=$(PORT))"
	@echo "  run-client        - run client against $(INIT_HOST):$(INIT_PORT)"
	@echo "  run-cluster       - testBash/10Server.sh"
	@echo "  test-leader-fail  - testBash/testLeaderFail.sh"
	@echo "  kill-ports        - kill stale java/rmiregistry processes on 1099-1110"

build:
	$(MVN) -q clean package -DskipTests

test:
	$(MVN) -q test

cucumber:
	$(MVN) -q test -Dtest=RunCucumberTest

clean:
	$(MVN) -q clean
	rm -rf __MACOSX out

$(JAR): build

run-init: $(JAR)
	java -jar $(JAR) $(INIT_ID) $(INIT_HOST) $(INIT_PORT) $(INIT_HOST) $(INIT_PORT) --init

run-node: $(JAR)
	java -jar $(JAR) $(ID) $(HOST) $(PORT) $(INIT_HOST) $(INIT_PORT)

run-client: $(JAR)
	java -cp $(JAR) manuel.rpckvstore.Client $(INIT_HOST) $(INIT_PORT)

run-cluster:
	cd testBash && bash 10Server.sh

test-leader-fail:
	cd testBash && bash testLeaderFail.sh

kill-ports:
	@for p in 1099 1100 1101 1102 1103 1104 1105 1106 1107 1108 1109 1110; do \
		pid=$$(lsof -ti tcp:$$p 2>/dev/null); \
		if [ -n "$$pid" ]; then echo "killing pid $$pid on $$p"; kill -9 $$pid; fi; \
	done
