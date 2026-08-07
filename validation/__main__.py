#!/usr/bin/env python3
"""Correctness gate: `python3 -m validation`.

Brings up ONE cluster and runs every correctness validator against it
(linearizability, missing_key, quorum_tolerance, retry_convergence), writes
metrics/validation_summary.txt, and exits non-zero if any property fails. One
shared bring-up (not four) keeps the gate fast; quorum_tolerance restarts the
nodes it stops so the shared cluster stays intact across validators.
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from ._harness import Config, PaxosClient, PaxosCluster
from . import linearizability, missing_key, quorum_tolerance, retry_convergence

# quorum_tolerance runs last: it stops/starts nodes, so anything reusing the
# cluster afterward would race the restarts.
VALIDATORS = [
    linearizability,
    missing_key,
    retry_convergence,
    quorum_tolerance,
]


def parse_args(argv=None):
    p = argparse.ArgumentParser(description="Paxos correctness gate (all validators)")
    p.add_argument("--config", choices=["clean", "chaos"], default="clean")
    p.add_argument("--cluster-size", type=int, default=10)
    p.add_argument("--keys", type=int, default=12)
    p.add_argument("--writers", type=int, default=6)
    p.add_argument("--reads", type=int, default=12)
    p.add_argument("--settle-seconds", type=int, default=12)
    p.add_argument("--no-manage-cluster", action="store_true")
    p.add_argument("--keep-up", action="store_true")
    return p.parse_args(argv)


def _report(results, cluster_size, config) -> tuple[str, bool]:
    lines = ["=" * 64,
             f"PAXOS CORRECTNESS REPORT  (config={config}, cluster={cluster_size})",
             "=" * 64]
    overall = True
    for r in results:
        ok = bool(r.get("pass"))
        overall = overall and ok
        lines.append(f"  {'PASS' if ok else 'FAIL':6} {r['name']}")
    lines.append("\n" + ("OVERALL: PASS" if overall
                         else "OVERALL: FAIL (see metrics/validation_results.json)"))
    return "\n".join(lines), overall


def main(argv=None) -> int:
    args = parse_args(argv)
    cfg = Config.load(cli_cluster_size=args.cluster_size)
    cluster = PaxosCluster(cfg)
    client = PaxosClient(cfg)
    manage = not args.no_manage_cluster

    if args.config != "clean":
        print("NOTE: correctness invariants are best judged under --config clean.",
              file=sys.stderr)

    results = []
    try:
        if manage:
            cluster.bring_up(args.config)
        else:
            print(f"Using already-up cluster ({cfg.cluster_size} nodes assumed).")
        for module in VALIDATORS:
            print(f"\n########## {module.__name__.split('.')[-1]} ##########")
            results.append(module.run(client, cluster, args))
    finally:
        if manage and not args.keep_up:
            cluster.tear_down()

    metrics_dir = Path(cfg.repo_root) / "metrics"
    metrics_dir.mkdir(exist_ok=True)
    (metrics_dir / "validation_results.json").write_text(
        json.dumps({"config": args.config, "cluster_size": cfg.cluster_size,
                    "results": results}, indent=2, default=str))

    report, overall = _report(results, cfg.cluster_size, args.config)
    print("\n" + report)
    (metrics_dir / "validation_summary.txt").write_text(report + "\n")
    print(f"\nWrote {metrics_dir}/validation_summary.txt")
    return 0 if overall else 1


if __name__ == "__main__":
    sys.exit(main())