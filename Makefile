# PAXOS Key-Value Store
#
# Two things to know:
#   1. The cluster runs in Docker. Knobs live in .env.
#   2. The Java JAR is built inside the Docker image; `make build` is only
#      needed if you want to run unit tests (`make test`) on the host.

MVN ?= mvn
JAR := target/KVStore2PC.jar

.PHONY: help build test up down client smoke thread load logs clean validate validate-paxos validate-paxos-easy validate-paxos-hard saturation metrics

help:
	@echo "Targets:"
	@echo "  build   - mvn clean package -> $(JAR)"
	@echo "  test    - mvn test (JUnit + Cucumber)"
	@echo "  up      - bring up CLUSTER_SIZE-node docker cluster"
	@echo "  down    - tear down the cluster"
	@echo "  client  - interactive REPL against the live cluster"
	@echo "  smoke   - PUT/GET round-trip assertion"
	@echo "  thread  - concurrent client self-check"
	@echo "  validate-paxos - correctness gate (python3 -m validation)"
	@echo "  saturation     - performance harness (T1/T2/T3, informational)"
	@echo "  validate       - fault-tolerance/latency matrix -> metrics/"
	@echo "  metrics        - render figures from metrics/ (generate_charts.py)"
	@echo "  logs    - follow docker compose logs"
	@echo "  clean   - mvn clean + remove generated docker-compose.yml"

build:
	$(MVN) -q clean package -DskipTests

test:
	$(MVN) -q test

docker-compose.yml: .env scripts/gen-compose.sh
	bash scripts/gen-compose.sh > docker-compose.yml

up: docker-compose.yml
	@. ./.env; \
	svcs=$$(seq 0 $$((CLUSTER_SIZE - 1)) | sed 's/^/node/' | tr '\n' ' '); \
	echo "Bringing up $$CLUSTER_SIZE nodes: $$svcs"; \
	docker compose up -d --build $$svcs

down:
	docker compose down -v --remove-orphans

client: docker-compose.yml
	bash scripts/client.sh

thread: docker-compose.yml
	bash scripts/threadedexample.sh
load: docker-compose.yml
	bash scripts/load.sh
smoke: docker-compose.yml
	bash scripts/dockertest.sh

logs:
	docker compose logs -f

clean:
	$(MVN) -q clean
	rm -f docker-compose.yml
	rm -rf metrics/
	rm -rf ./img 
	rm -rf ./logs
validate: clean 
	bash scripts/validate.sh
 
metrics:
	python3 scripts/generate_charts.py

# Correctness gate: one cluster, all four validators
# (linearizability, missing_key, quorum_tolerance, retry_convergence).
validate-paxos: 
	rm -rf ./logs
	python3 -m validation

# Performance (informational, not a gate): T1 sweep / T2 drift / T3 recovery.
saturation: clean
	rm -rf ./logs
	python3 scripts/saturation.py --config chaos
