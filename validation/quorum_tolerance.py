#!/usr/bin/env python3
"""Quorum tolerance: reads survive up to floor((N-1)/2) node failures.

Mirrors the kill order in scripts/validate.sh: node0 (the query target) and
node<N-1> (the max-id leader) always survive; the MIDDLE nodes are stopped one
at a time so membership stays N and the majority threshold stays N/2+1. After
each kill (and a settle) every seeded key must still GET back its exact value,
right up to floor((N-1)/2) failures -- the majority boundary. Stopped nodes are
restarted at the end so the shared cluster is left intact for other validators.
"""
from __future__ import annotations

import sys
import time
import uuid

from ._harness import hash12
from ._cli import standalone


def _seed(client, count):
    keys = {}
    for i in range(count):
        key, value = f"q{uuid.uuid4().hex[:8]}", f"qv{i}-{uuid.uuid4().hex[:6]}"
        client.run_op("node0", 0, "PUT", key, value)
        keys[key] = value
    return keys


def _verify_all(client, keys):
    """Every seeded key must GET back its exact value on node0."""
    mismatches = []
    for key, value in keys.items():
        r = client.run_op("node0", 0, "GET", key)[-1]
        if not r.success or r.resp_hash != hash12(value):
            mismatches.append({"key": key, "success": r.success,
                               "resp_hash": r.resp_hash, "want": hash12(value),
                               "error": r.error})
    return mismatches


def run(client, cluster, args) -> dict:
    n = client.cfg.cluster_size
    max_tolerated = (n - 1) // 2
    print(f"- quorum_tolerance: N={n}, tolerating up to {max_tolerated} failures ...")

    keys = _seed(client, max(4, args.keys // 2))
    baseline = _verify_all(client, keys)

    steps = []
    stopped = []
    survived = True
    try:
        for failed in range(1, max_tolerated + 1):
            victim = f"node{n - 1 - failed}"   # middle nodes N-2 .. 1
            print(f"    stopping {victim} (failed={failed}/{max_tolerated})")
            cluster.stop_node(victim)
            stopped.append(victim)
            time.sleep(args.settle_seconds)
            mismatches = _verify_all(client, keys)
            steps.append({"failed": failed, "victim": victim,
                          "mismatches": mismatches[:20], "ok": not mismatches})
            if mismatches:
                survived = False
    finally:
        for victim in stopped:
            cluster.start_node(victim)

    return {"name": "quorum_tolerance (reads survive floor((N-1)/2) failures)",
            "pass": survived and not baseline,
            "cluster_size": n, "max_tolerated": max_tolerated,
            "seeded_keys": len(keys), "baseline_mismatches": baseline[:20],
            "steps": steps}


def main(argv=None) -> int:
    return standalone(run, "Reads survive floor((N-1)/2) node failures", argv)


if __name__ == "__main__":
    sys.exit(main())