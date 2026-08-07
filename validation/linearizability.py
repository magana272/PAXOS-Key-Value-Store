#!/usr/bin/env python3
"""Linearizability: concurrent PUTs to one key choose exactly one winner, and
that winner is never lost or resurrected.

Ported from the old SafetySuite.s2_single_winner + s3_no_lost_no_resurrect.
N writers race distinct values to one key; reads across nodes must observe a
single value drawn from the proposed set, a follow-up GET must return that
winner (never stale/null), and after a DELETE the key must not resurrect.
"""
from __future__ import annotations

import sys
import uuid

from ._harness import PaxosClient, evaluate_single_winner, hash12, MISSING_SENTINEL
from ._cli import standalone


def _single_winner(client, n, keys, writers, reads):
    results = []
    for _ in range(keys):
        key = f"cf{uuid.uuid4().hex[:8]}"
        values = [f"writer{i}-{uuid.uuid4().hex[:6]}" for i in range(writers)]
        client.run_many(
            [dict(op="PUT", key=key, value=values[i], **PaxosClient.node(i, n))
             for i in range(writers)],
            writers,
        )
        read_specs = [dict(op="GET", key=key, **PaxosClient.node(i, n))
                      for i in range(reads)]
        verdict = evaluate_single_winner(client.run_many(read_specs, reads), values)
        verdict["key"] = key
        verdict["proposed_values"] = values
        results.append(verdict)

    conclusive = [r for r in results if not r["inconclusive"]]
    failures = [r for r in conclusive
                if not (r["single_winner"] and r["from_proposed"])]
    return {"pass": bool(conclusive) and not failures,
            "checked": keys, "conclusive": len(conclusive),
            "violations": failures[:20], "sample": results[:5]}


def _no_lost_no_resurrect(client, n, keys):
    lost, resurrected = [], []
    for _ in range(keys):
        token = uuid.uuid4().hex[:8]
        key, value = f"lr{token}", f"v{token}"
        client.run_op("node0", 0, "PUT", key, value)
        for node in range(4):
            r = client.run_op(**PaxosClient.node(node, n), op="GET", key=key)[-1]
            if r.success and r.resp_hash != hash12(value):
                lost.append(key)
        client.run_op("node0", 0, "DELETE", key)
        for node in range(4):
            r = client.run_op(**PaxosClient.node(node, n), op="GET", key=key)[-1]
            if r.success and r.resp_hash != hash12(MISSING_SENTINEL):
                resurrected.append(key)
    return {"pass": not lost and not resurrected, "checked": keys,
            "lost_updates": lost[:20], "resurrected": resurrected[:20]}


def run(client, cluster, args) -> dict:
    n = client.cfg.cluster_size
    print("- linearizability: single-winner under conflict ...")
    winner = _single_winner(client, n, args.keys, args.writers, args.reads)
    print("- linearizability: no lost update / no resurrection ...")
    durability = _no_lost_no_resurrect(client, n, max(3, args.keys // 2))
    return {
        "name": "linearizability (single-winner + no-loss/no-resurrect)",
        "pass": winner["pass"] and durability["pass"],
        "single_winner": winner,
        "no_lost_no_resurrect": durability,
    }


def main(argv=None) -> int:
    return standalone(run, "Linearizability: single winner, never lost", argv)


if __name__ == "__main__":
    sys.exit(main())