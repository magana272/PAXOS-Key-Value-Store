# PAXOS KEY-VALUE STORE

A replicated key-value store backed by the Paxos consensus protocol. Every node runs all three Paxos roles (Proposer, Acceptor, Learner) over Java RMI. The cluster is containerized: one Paxos node per Docker container on a user-defined bridge network.

## Quick Start

```shell
make up         # build image, bring up a CLUSTER_SIZE-node cluster
make client     # interactive REPL: put / get / delete
make smoke      # automated PUT/GET round-trip assertion
make down       # tear it all down
```

Client REPL session:

```text
paxos> put HELLO WORLD
KEY Value Successfully Set
paxos> get HELLO
WORLD
paxos> delete HELLO
Key-Value Successfully Deleted
paxos> exit
```

## Configuration

All knobs live in `.env`:

```env
CLUSTER_SIZE=5     # any positive integer; node0 is always the init node
ACCEPT_FAIL=0.0    # simulated acceptor failure rate per round
PROPOSE_FAIL=0.0   # simulated proposer failure rate per round
```

`docker-compose.yml` is generated from `.env` by `scripts/gen-compose.sh` on every `make up`. `ACCEPT_FAIL` and `PROPOSE_FAIL` are substituted into the running containers at compose time; change them and `make up` again to exercise the retry / leader-fail-over path.

## Overview of Paxos

Every node in the cluster runs all three Paxos roles at once: it is a **Proposer**, an **Acceptor**, and a **Learner**. One node is elected **Leader** and is the only one that drives client transactions.

### Protocol

The leader runs three phases for every transaction. A strict majority of acceptors must respond at every phase for the value to be chosen, and the protocol retries the whole sequence with a fresh sequence number if any phase falls short.

1. **Phase 1 - Propose (Prepare).** The leader picks a sequence number `n` larger than anything it has used and calls `Propose(n)` on every acceptor. An acceptor that has not promised a higher number records `n` as its new `PromisedSequenceNumber` and replies `Ack.YES`. An acceptor that has already promised a higher number replies `Ack.NO`.
2. **Phase 2 - Accept.** Once the leader collects a majority of `YES` votes, it calls `Accept(n, v)` on every acceptor with the proposed value `v`. Acceptors whose promise still matches `n` reply with the accepted packet; stale proposals are returned with the response field set to `Ignored`. This is the phase wrapped in the simulated `ACCEPT_FAIL` failure on each acceptor, so the leader counts successful accepts against a majority threshold before continuing.
3. **Phase 3 - Learn.** With a majority of accepts, the leader calls `Learn(v)` on every node, which dispatches the PUT / GET / DELETE through `KeyValueStore` and emits the response back to the client. If the leader did not get a majority of accepts the whole sequence is retried with a fresh `n`.

```mermaid
flowchart LR
    C[Client]:::client -->|PUT / GET / DELETE| L((Leader Proposer)):::leader
    L -->|prepare n| A1[Acceptor 1]:::acceptor
    L -->|prepare n| A2[Acceptor 2]:::acceptor
    L -->|prepare n| A3[Acceptor 3]:::acceptor
    A1 -->|promise| L
    A2 -->|promise| L
    A3 -->|promise| L
    L -->|accept n,v| A1
    L -->|accept n,v| A2
    L -->|accept n,v| A3
    A1 -.->|learn v| LR[(Replicated KV)]:::kv
    A2 -.->|learn v| LR
    A3 -.->|learn v| LR
    classDef client fill:#bbdefb,stroke:#1976d2,color:#0d47a1
    classDef leader fill:#ffd54f,stroke:#f57c00,color:#bf360c
    classDef acceptor fill:#c8e6c9,stroke:#388e3c,color:#1b5e20
    classDef kv fill:#e1bee7,stroke:#7b1fa2,color:#4a148c
```

### Normal Happy Path

The leader drives a three-phase exchange. A majority of acceptors must reply at every phase for the value to be committed.

```mermaid
sequenceDiagram
    autonumber
    box rgb(187,222,251) Client
        participant C as Client
    end
    box rgb(255,213,79) Leader Proposer
        participant L as Leader
    end
    box rgb(200,230,201) Acceptors
        participant A1 as Acceptor 1
        participant A2 as Acceptor 2
        participant A3 as Acceptor 3
    end

    C->>L: PUT key=k value=v
    Note over L,A3: Phase 1 - Propose
    L->>A1: prepare(n)
    L->>A2: prepare(n)
    L->>A3: prepare(n)
    A1-->>L: promise(n)
    A2-->>L: promise(n)
    A3-->>L: promise(n)

    Note over L,A3: Phase 2 - Accept
    L->>A1: accept(n, v)
    L->>A2: accept(n, v)
    L->>A3: accept(n, v)
    A1-->>L: accepted
    A2-->>L: accepted
    A3-->>L: accepted

    Note over L,A3: Phase 3 - Learn
    L->>A1: learn(v)
    L->>A2: learn(v)
    L->>A3: learn(v)
    L-->>C: KEY Value Successfully Set
```

### On Acceptor Failure

Acceptors fail at random with probability `ACCEPT_FAIL` per accept. As long as a strict majority still responds, consensus is reached and the value is committed across the network.

```mermaid
sequenceDiagram
    autonumber
    box rgb(255,213,79) Leader Proposer
        participant L as Leader
    end
    box rgb(200,230,201) Acceptors
        participant A1 as Acceptor 1
        participant A3 as Acceptor 3
    end
    box rgb(255,205,210) Failing acceptor
        participant A2 as Acceptor 2
    end

    L->>A1: accept(n, v)
    L->>A2: accept(n, v)
    L->>A3: accept(n, v)
    A1-->>L: accepted
    A2--xL: dropped (simulated fail)
    A3-->>L: accepted
    Note over L: 2 of 3 = majority, consensus reached
    L-->>L: commit v
```

### Leader Failing

Before a node informs the leader, it checks `leader.isAlive()`. If the leader does not respond, the node assumes it is dead, removes it from the network, informs the other nodes of the new state, and runs a fresh leader election. The new leader then takes over driving transactions. You can demonstrate this with `docker compose stop nodeN` while a client is running.

```mermaid
flowchart TD
    Start([Client request lands on a node]):::client --> Check{leader.isAlive?}
    Check -- yes --> Forward[Forward transaction to leader]:::leader
    Check -- no response --> Remove[Remove leader from nodeAddresses]:::fail
    Remove --> Inform[Inform peers of new state]:::acceptor
    Inform --> Elect[runLeaderElection]:::leader
    Elect --> NewLeader[New leader chosen]:::leader
    NewLeader --> Forward
    Forward --> Done([Phase 1 / 2 / 3 proceeds normally]):::acceptor
    classDef client fill:#bbdefb,stroke:#1976d2,color:#0d47a1
    classDef leader fill:#ffd54f,stroke:#f57c00,color:#bf360c
    classDef acceptor fill:#c8e6c9,stroke:#388e3c,color:#1b5e20
    classDef fail fill:#ffcdd2,stroke:#c62828,color:#b71c1c
```

### Leader Election

Election itself is a deterministic **max-ID wins** rule, not a voting round. Every node runs `runLeaderElection()` in `Node.java` independently and walks its own `nodeAddresses` set, picking the `NodeAddress` whose numeric ID is the largest. Because every node keeps the same view of `nodeAddresses` through the `inform` / `informOfNewNode` flow, all of them converge on the same leader without exchanging extra messages.

It is triggered in three situations:

- The very first transaction lands on a node and `leader` is still `null` (first call to `Put` / `Get` / `Delete`).
- `leader.isAlive()` returns `false`. That call is an RMI lookup for `Node-<id>` against the recorded IP and port (see `NodeAddress.isAlive()`); any `RemoteException` or `NotBoundException` is treated as dead.
- A peer notices the leader is gone, removes it from its own `nodeAddresses`, calls `informOfNewNode()` to push the new view to everyone, and reruns the election.

```mermaid
flowchart TD
    T[Election triggered]:::fail --> Init["leaderID = -1<br/>maxleader = null"]
    Init --> Iter[For each node in nodeAddresses]:::acceptor
    Iter --> Cmp{"Integer(node.id) > leaderID?"}
    Cmp -- yes --> Upd["leaderID = Integer(node.id)<br/>maxleader = node"]:::leader
    Cmp -- no --> Skip[skip]
    Upd --> Cont{More nodes?}
    Skip --> Cont
    Cont -- yes --> Iter
    Cont -- no --> Set["this.leader = maxleader"]:::leader
    Set --> Resume([Resume transaction with new leader]):::client
    classDef client fill:#bbdefb,stroke:#1976d2,color:#0d47a1
    classDef leader fill:#ffd54f,stroke:#f57c00,color:#bf360c
    classDef acceptor fill:#c8e6c9,stroke:#388e3c,color:#1b5e20
    classDef fail fill:#ffcdd2,stroke:#c62828,color:#b71c1c
```

A node that has not yet been informed of the latest membership change can briefly disagree about who the leader is, which is why the first thing the leader does on a new transaction is verify `leader.isAlive()` and rerun the election if it does not match.

## Make Targets

| Target | What it does |
|--------|--------------|
| `make build` | `mvn clean package` -> `target/KVStore2PC.jar` |
| `make test` | `mvn test` (JUnit + Cucumber) |
| `make up` | bring up CLUSTER_SIZE-node docker cluster |
| `make down` | tear down the cluster |
| `make client` | interactive REPL against the live cluster |
| `make smoke` | PUT/GET round-trip assertion |
| `make logs` | follow `docker compose logs` |
| `make clean` | `mvn clean` + remove generated `docker-compose.yml` |

## Tests

`make test` runs the Maven suite:

- **NodeTest** (JUnit 5): single-node coverage of the constructor, KeyValueStore semantics (put / get / delete, missing-key sentinel, duplicate refusal), and `Propose()` promise tracking.
- **WritePathTest** (JUnit 5): in-process exercise of the PROPOSE / ACCEPT / LEARN write path with zero simulated failure rate. Covers PUT propagation, stale-sequence rejection, idempotent delete, duplicate-put refusal, and GET as a no-op.
- **RunCucumberTest** (Cucumber + JUnit Platform Suite): BDD scenarios in `src/test/resources/features/kvstore.feature` backed by `KVStoreSteps`.

Multi-node end-to-end coverage is the docker `make smoke`.