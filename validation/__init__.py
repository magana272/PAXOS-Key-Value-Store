"""Correctness validators for the Paxos KV cluster.

Each module is one linearizability property, exposing:
  - run(client, cluster, args) -> dict   (pass/fail + evidence)
  - main()                                (standalone against an up cluster)

Run the whole gate with a single shared cluster via `python3 -m validation`.
Performance (saturation) lives separately in scripts/saturation.py; charting
in scripts/generate_charts.py.
"""
from ._harness import (
    Config,
    OpResult,
    PaxosClient,
    PaxosCluster,
    MISSING_SENTINEL,
    PUT_ACK,
    DELETE_ACK,
    hash12,
    evaluate_single_winner,
    summarize,
)

__all__ = [
    "Config",
    "OpResult",
    "PaxosClient",
    "PaxosCluster",
    "MISSING_SENTINEL",
    "PUT_ACK",
    "DELETE_ACK",
    "hash12",
    "evaluate_single_winner",
    "summarize",
]