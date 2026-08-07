#!/usr/bin/env python3
"""Retry convergence: repeated/concurrent writes of the same key converge to a
single value with no split-brain, even when the proposer has to retry.

Ported from SafetySuite.s5_idempotent_same_value with a retry angle. Many
writers issue the *same* value concurrently (idempotence): the final GET must
return that value. Then many writers issue *distinct* values concurrently and
the run repeats; reads must still converge to a single winner from the proposed
set. Under --config chaos the Java proposer actually retries (message loss), so
this exercises the retry path rather than just the happy one.
"""
from __future__ import annotations

import sys
import uuid

from ._harness import PaxosClient, evaluate_single_winner, hash12
from ._cli import standalone


def _idempotent_same_value(client, n, keys, writers):
    offenders = []
    for _ in range(keys):
        token = uuid.uuid4().hex[:8]
        key, value = f"idem{token}", f"v{token}"
        client.run_many(
            [dict(op="PUT", key=key, value=value, **PaxosClient.node(i, n))
             for i in range(writers)],
            writers,
        )
        result = client.run_op("node0", 0, "GET", key)[-1]
        if not result.success or result.resp_hash != hash12(value):
            offenders.append(key)
    return {"pass": not offenders, "checked": keys, "offenders": offenders[:20]}


def _converges_under_retry(client, n, keys, writers, reads):
    results = []
    for _ in range(keys):
        key = f"rc{uuid.uuid4().hex[:8]}"
        values = [f"w{i}-{uuid.uuid4().hex[:6]}" for i in range(writers)]
        # Two concurrent waves against the same key to force contention/retry.
        for _wave in range(2):
            client.run_many(
                [dict(op="PUT", key=key, value=values[i], **PaxosClient.node(i, n))
                 for i in range(writers)],
                writers,
            )
        read_specs = [dict(op="GET", key=key, **PaxosClient.node(i, n))
                      for i in range(reads)]
        verdict = evaluate_single_winner(client.run_many(read_specs, reads), values)
        verdict["key"] = key
        results.append(verdict)
    conclusive = [r for r in results if not r["inconclusive"]]
    failures = [r for r in conclusive
                if not (r["single_winner"] and r["from_proposed"])]
    return {"pass": bool(conclusive) and not failures,
            "checked": keys, "conclusive": len(conclusive),
            "violations": failures[:20]}


def run(client, cluster, args) -> dict:
    n = client.cfg.cluster_size
    print("- retry_convergence: same-value idempotence ...")
    idem = _idempotent_same_value(client, n, max(3, args.keys // 2), args.writers)
    print("- retry_convergence: distinct writes converge to one winner ...")
    conv = _converges_under_retry(client, n, max(3, args.keys // 2),
                                  args.writers, args.reads)
    return {"name": "retry_convergence (idempotent + converges to single winner)",
            "pass": idem["pass"] and conv["pass"],
            "idempotent_same_value": idem,
            "converges_under_retry": conv}


def main(argv=None) -> int:
    return standalone(run, "Repeated writes converge to a single value", argv)


if __name__ == "__main__":
    sys.exit(main())