#!/usr/bin/env python3
"""Missing-key contract: a GET on a never-PUT key returns exactly the missing
sentinel ("KEY does not exist") on every node.

The harness identifies values by SHA-1 of the response body, so a missing read
must hash to hash12(MISSING_SENTINEL). Any other hash means the node invented a
value, returned a stale one, or errored -- all violations of the read contract.
"""
from __future__ import annotations

import sys
import uuid

from ._harness import PaxosClient, hash12, MISSING_SENTINEL
from ._cli import standalone


def run(client, cluster, args) -> dict:
    n = client.cfg.cluster_size
    print("- missing_key: fresh never-PUT keys read as the sentinel ...")
    want = hash12(MISSING_SENTINEL)
    offenders = []
    nodes = min(n, 4)
    for _ in range(args.keys):
        key = f"missing{uuid.uuid4().hex[:8]}"
        for node in range(nodes):
            r = client.run_op(**PaxosClient.node(node, n), op="GET", key=key)[-1]
            if not r.success or r.resp_hash != want:
                offenders.append({"key": key, "node": node,
                                  "success": r.success, "resp_hash": r.resp_hash,
                                  "error": r.error})
    return {"name": "missing_key sentinel contract",
            "pass": not offenders, "checked": args.keys, "nodes": nodes,
            "expected_hash": want, "offenders": offenders[:20]}


def main(argv=None) -> int:
    return standalone(run, "Missing key returns the sentinel on every node", argv)


if __name__ == "__main__":
    sys.exit(main())