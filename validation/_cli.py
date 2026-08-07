#!/usr/bin/env python3
"""Shared CLI plumbing for the standalone validator entry points.

Every validator module can call `standalone(run)` from its `main()` to get the
same flags (cluster size, config preset, --no-manage-cluster, the workload
knobs) and the same bring-up / tear-down behavior, so each property is runnable
on its own against a fresh or an already-up cluster.
"""
from __future__ import annotations

import argparse
import json
import sys

from ._harness import Config, PaxosClient, PaxosCluster


def build_parser(description: str) -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description=description)
    p.add_argument("--config", choices=["clean", "chaos"], default="clean")
    p.add_argument("--cluster-size", type=int, default=5)
    p.add_argument("--keys", type=int, default=12)
    p.add_argument("--writers", type=int, default=6)
    p.add_argument("--reads", type=int, default=12)
    p.add_argument("--settle-seconds", type=int, default=12,
                   help="wait after stopping a node before the first verify read")
    p.add_argument("--no-manage-cluster", action="store_true",
                   help="use an already-up cluster instead of bringing one up")
    p.add_argument("--keep-up", action="store_true")
    return p


def standalone(run, description: str, argv=None) -> int:
    """Parse args, resolve a cluster, run one validator, print its verdict.

    `run` is the module's run(client, cluster, args) -> dict.
    Returns 0 if the verdict passed, 1 otherwise.
    """
    args = build_parser(description).parse_args(argv)
    cfg = Config.load(cli_cluster_size=args.cluster_size)
    cluster = PaxosCluster(cfg)
    client = PaxosClient(cfg)
    manage = not args.no_manage_cluster

    verdict = None
    try:
        if manage:
            cluster.bring_up(args.config)
        else:
            print(f"Using already-up cluster ({cfg.cluster_size} nodes assumed).")
        verdict = run(client, cluster, args)
    finally:
        if manage and not args.keep_up:
            cluster.tear_down()

    print(json.dumps(verdict, indent=2, default=str))
    passed = bool(verdict) and verdict.get("pass")
    print("PASS" if passed else "FAIL")
    return 0 if passed else 1