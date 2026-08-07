# PAXOS Key-Value Store
#
# Two things to know:
#   1. The cluster runs in Docker. Knobs live in .env.
#   2. The Java JAR is built inside the Docker image; `make build` is only
#      needed if you want to run unit tests (`make test`) on the host.
#
# CLUSTER_NAME selects an isolated, named cluster: its own docker compose
# project (-p), network (<name>_paxos_net), per-node volumes (logs/<name>/node#)
# and per-run metrics/<name> + img/<name>. Pass CLUSTER_NAME=<name> (and
# optionally CLUSTER_SIZE=<n>) to any cluster target; default is "default".
# smoke/thread/load create and destroy their own throwaway cluster, so they use
# their own names and never disturb a persistent cluster you brought up.

MVN ?= mvn
JAR := target/KVStore2PC.jar

ANALYSIS_IMAGE ?= paxos-analysis:latest
PYRUN ?= docker run --rm \
	-v $(CURDIR):$(CURDIR) -w $(CURDIR) \
	-v /var/run/docker.sock:/var/run/docker.sock \
	-e HOME=/tmp $(ANALYSIS_IMAGE)

CLUSTER_NAME ?= default
COMPOSE_FILE := docker-compose.$(CLUSTER_NAME).yml
DC := docker compose -p $(CLUSTER_NAME) -f $(COMPOSE_FILE)
GEN = CLUSTER_NAME=$(CLUSTER_NAME) $(if $(strip $(CLUSTER_SIZE)),CLUSTER_SIZE=$(CLUSTER_SIZE),) bash scripts/gen-compose.sh > $(COMPOSE_FILE)

.PHONY: help build test up add-node down client smoke thread load logs clean clean-all validate validate-paxos saturation metrics all analysis-image

help:
	@echo "Targets (pass CLUSTER_NAME=<name> CLUSTER_SIZE=<n> to scope a cluster):"
	@echo "  build     - mvn clean package -> $(JAR)"
	@echo "  test      - mvn test (JUnit + Cucumber)"
	@echo "  up        - bring up a CLUSTER_SIZE-node cluster named CLUSTER_NAME"
	@echo "  add-node  - add one node to cluster CLUSTER_NAME"
	@echo "  down      - tear down cluster CLUSTER_NAME"
	@echo "  client    - interactive REPL against cluster CLUSTER_NAME"
	@echo "  smoke     - PUT/GET round-trip (own throwaway cluster)"
	@echo "  thread    - concurrent client self-check (own throwaway cluster)"
	@echo "  validate-paxos - correctness gate (python3 -m validation)"
	@echo "  saturation     - performance harness (T1/T2/T3, informational)"
	@echo "  validate       - fault-tolerance/latency matrix -> metrics/validate"
	@echo "  metrics        - render figures from metrics/<CLUSTER_NAME>"
	@echo "  logs      - follow cluster CLUSTER_NAME logs"
	@echo "  clean     - remove cluster CLUSTER_NAME artifacts"
	@echo "  clean-all - wipe all generated clusters/metrics/logs (keeps committed img/*.png)"
	@echo "  analysis-image - build the Python analysis/orchestration image"

analysis-image:
	docker build -f Dockerfile.analysis -t $(ANALYSIS_IMAGE) .

build:
	$(MVN) -q clean package -DskipTests

test:
	$(MVN) -q test

up:
	$(GEN)
	$(DC) up -d --build
	@echo "Cluster '$(CLUSTER_NAME)' is up on network $(CLUSTER_NAME)_paxos_net"

add-node:
	@current=$$(grep -cE '^  node[0-9]+:' $(COMPOSE_FILE) 2>/dev/null || echo 0); \
	if [ "$$current" -lt 1 ]; then \
		echo "No cluster '$(CLUSTER_NAME)' found; run: make up CLUSTER_NAME=$(CLUSTER_NAME)" >&2; exit 1; \
	fi; \
	newsize=$$((current + 1)); \
	echo "Adding node$$current to cluster '$(CLUSTER_NAME)' (size $$current -> $$newsize)"; \
	CLUSTER_NAME=$(CLUSTER_NAME) CLUSTER_SIZE=$$newsize bash scripts/gen-compose.sh > $(COMPOSE_FILE); \
	$(DC) up -d --build node$$current

down:
	$(GEN)
	$(DC) down -v --remove-orphans

client:
	$(GEN)
	CLUSTER_NAME=$(CLUSTER_NAME) PAXOS_NET=$(CLUSTER_NAME)_paxos_net bash scripts/client.sh

smoke:
	bash scripts/dockertest.sh

thread:
	bash scripts/threadedexample.sh

load:
	bash scripts/load.sh

logs:
	$(GEN)
	$(DC) logs -f

clean:
	-$(DC) down -v --remove-orphans >/dev/null 2>&1 || true
	rm -f $(COMPOSE_FILE)
	rm -rf logs/$(CLUSTER_NAME) metrics/$(CLUSTER_NAME) img/$(CLUSTER_NAME)

clean-all:
	$(MVN) -q clean
	rm -f docker-compose.*.yml docker-compose.yml
	rm -rf metrics/ ./logs
	find img -mindepth 1 -maxdepth 1 -type d -exec rm -rf {} + 2>/dev/null || true

# Fault-tolerance / latency matrix. Runs on its own "validate" cluster and writes
# to metrics/validate + img/validate so it never clobbers other targets.
validate: analysis-image
	CLUSTER_NAME=validate bash scripts/validate.sh
	$(PYRUN) python3 scripts/generate_charts.py --metrics-dir metrics/validate --img-dir img/validate

metrics: analysis-image
	$(PYRUN) python3 scripts/generate_charts.py --metrics-dir metrics/$(CLUSTER_NAME) --img-dir img/$(CLUSTER_NAME)

# Correctness gate: one cluster, all four validators
# (linearizability, missing_key, quorum_tolerance, retry_convergence).
validate-paxos: analysis-image
	$(PYRUN) python3 -m validation --cluster-name validate-paxos

# Performance (informational, not a gate): T1 sweep / T2 drift / T3 recovery.
saturation: analysis-image
	rm -rf logs/saturation
	$(PYRUN) python3 scripts/saturation.py --cluster-name saturation --config chaos

# Each gate runs on its own isolated cluster, so they no longer clobber each
# other's logs/metrics; run them back to back.
all:
	$(MAKE) validate
	$(MAKE) validate-paxos
	$(MAKE) saturation
