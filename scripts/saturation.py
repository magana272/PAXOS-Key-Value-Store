#!/usr/bin/env python3
"""Performance harness for the Paxos KV cluster (NOT a correctness gate).

SaturationSuite: T1 concurrency sweep,
T2 sustained-load drift / thread-leak detector, T3 recovery after burst.

Itimports the shared infrastructure from the validation package so there is one
Docker client / cluster lifecycle. Correctness lives in `python3 -m validation`;
this file only measures latency / throughput / drift and renders figures.

Usage:
  python3 scripts/saturation.py --sweep 1,2,4,8,16 --config chaos
"""
from __future__ import annotations

import argparse
import json
import sys
import time
from collections import defaultdict
from pathlib import Path

import pandas as pd

# Allow running as a bare script (python3 scripts/saturation.py) by putting the
# repo root on the path so the validation package resolves.
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from validation._harness import (  # noqa: E402
    Config, PaxosClient, PaxosCluster, slope, summarize,
)


class SaturationSuite:
    def __init__(self, client: PaxosClient, cluster_size: int):
        self.client = client
        self.n = cluster_size

    @staticmethod
    def find_saturation_point(levels, err_threshold=0.05):
        return next((l["concurrency"] for l in levels
                     if l["error_rate"] > err_threshold), None)

    @staticmethod
    def detect_drift(series) -> dict:
        df = pd.DataFrame(series)
        p95_slope = slope(df.get("window"), df.get("p95_ms"))
        err_slope = slope(df.get("window"), df.get("error_rate"))
        return {"p95_slope_ms_per_window": round(p95_slope, 2),
                "err_slope_per_window": round(err_slope, 4),
                "drift_detected": (p95_slope > 5.0) or (err_slope > 0.02)}

    def seed_keyspace(self, n) -> list:
        import uuid
        keys = [f"sat{i}{uuid.uuid4().hex[:4]}" for i in range(n)]
        for i, k in enumerate(keys):
            self.client.run_op("node0", 0, "PUT", k, f"seed{i}")
        return keys

    def _level_specs(self, concurrency, repeats, keyspace) -> list:
        import uuid
        specs = []
        for c in range(concurrency):
            key = keyspace[c % len(keyspace)]
            nd = PaxosClient.node(c, self.n)
            if c % 2 == 0:
                specs.append(dict(op="GET", key=key, repeats=repeats, **nd))
            else:
                specs.append(dict(op="PUT", key=key,
                                  value=f"c{c}v{uuid.uuid4().hex[:6]}",
                                  repeats=repeats, **nd))
        return specs

    def run_level(self, concurrency, repeats, keyspace) -> dict:
        t0 = time.time()
        rows = self.client.run_many(
            self._level_specs(concurrency, repeats, keyspace), concurrency)
        wall = time.time() - t0
        st = summarize(rows)
        st["concurrency"] = concurrency
        st["wall_s"] = round(wall, 3)
        st["throughput_ops_s"] = round(st["ok"] / wall, 2) if wall > 0 else 0.0
        return st

    def t1_sweep(self, sweep, repeats, keyspace, err_threshold=0.05) -> dict:
        print(f"- T1 concurrency sweep {sweep} (repeats={repeats}) ...")
        levels = []
        for c in sweep:
            st = self.run_level(c, repeats, keyspace)
            print(f"    C={c:>3}: err={st['error_rate']*100:5.1f}%  "
                  f"p95={st['p95_ms']}ms  tput={st['throughput_ops_s']} ops/s")
            levels.append(st)
        sp = self.find_saturation_point(levels, err_threshold)
        return {"name": "T1 latency/error vs concurrency", "levels": levels,
                "err_threshold": err_threshold, "saturation_point": sp,
                "pass": sp is None,
                "note": ("no level exceeded the error threshold" if sp is None
                         else f"error rate crossed {err_threshold*100:.0f}% at concurrency={sp}")}

    def t2_drift(self, concurrency, drift_seconds, repeats, keyspace, preset,
                 window=15) -> dict:
        print(f"- T2 sustained-load drift: C={concurrency} for {drift_seconds}s "
              f"(preset={preset}) ...")
        buckets = defaultdict(list)
        start = time.time()
        while time.time() - start < drift_seconds:
            t0 = time.time()
            rows = self.client.run_many(
                self._level_specs(concurrency, repeats, keyspace), concurrency)
            buckets[int((t0 - start) // window)].extend(rows)
        series = []
        for w in sorted(buckets):
            st = summarize(buckets[w])
            st["window"], st["t_start_s"] = w, w * window
            series.append(st)
        drift = self.detect_drift(series)
        return {"name": "T2 sustained-load drift / thread-leak detector",
                "preset": preset, "concurrency": concurrency, "window_s": window,
                "series": series, **drift, "pass": not drift["drift_detected"],
                "note": ("upward drift over time suggests leaked/blocked pool threads"
                         if drift["drift_detected"]
                         else "no upward drift; pools appear stable")}

    def t3_recovery(self, baseline_c, burst_c, repeats, keyspace) -> dict:
        print(f"- T3 recovery: baseline C={baseline_c} -> burst C={burst_c} -> baseline ...")
        base = self.run_level(baseline_c, repeats, keyspace)
        self.run_level(burst_c, repeats, keyspace)
        after = self.run_level(baseline_c, repeats, keyspace)
        b, a = base["p95_ms"], after["p95_ms"]
        recovered = (a is not None and b is not None and a <= max(b * 2.0, b + 50))
        return {"name": "T3 recovery after burst", "baseline": base, "after": after,
                "pass": bool(recovered),
                "note": (f"post-burst p95 {a}ms vs baseline {b}ms"
                         + ("" if recovered else " -- did not recover (threads not freed?)"))}

    def run(self, args, preset: str) -> dict:
        print("\n########## SATURATION SUITE ##########")
        keyspace = self.seed_keyspace(max(4, len(args.sweep)))
        t1 = self.t1_sweep(args.sweep, args.repeats, keyspace)
        drift_c = min(max(args.sweep), max(self.n, 10))
        t2 = self.t2_drift(drift_c, args.drift_seconds,
                           max(2, args.repeats // 2), keyspace, preset)
        t3 = self.t3_recovery(1, max(args.sweep), args.repeats, keyspace)
        return {"T1": t1, "T2": t2, "T3": t3}


def figures(saturation, repo_root, cluster_name="saturation"):
    try:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
        import seaborn as sns
    except Exception as e:
        print(f"matplotlib/seaborn unavailable ({e}); skipping figures",
              file=sys.stderr)
        return
    sns.set_theme(style="whitegrid")
    img_dir = Path(repo_root) / "img" / cluster_name
    img_dir.mkdir(parents=True, exist_ok=True)

    def save(fig, name):
        fig.tight_layout()
        fig.savefig(img_dir / name, dpi=150)
        plt.close(fig)
        print(f"Wrote {img_dir}/{name}")

    levels = pd.DataFrame(saturation["T1"]["levels"])

    fig, ax = plt.subplots(figsize=(7, 5))
    melted = levels.melt(id_vars="concurrency",
                         value_vars=["p50_ms", "p95_ms", "p99_ms"],
                         var_name="percentile", value_name="latency_ms")
    melted["percentile"] = melted["percentile"].str.replace("_ms", "")
    sns.lineplot(data=melted, x="concurrency", y="latency_ms",
                 hue="percentile", marker="o", ax=ax)
    ax.set(xlabel="Concurrent clients", ylabel="Latency (ms)",
           title="Latency vs concurrency")
    save(fig, "latency_vs_concurrency.png")

    fig, ax = plt.subplots(figsize=(7, 5))
    sns.lineplot(x=levels["concurrency"], y=levels["error_rate"] * 100,
                 marker="o", color="#c0392b", ax=ax)
    sp = saturation["T1"].get("saturation_point")
    if sp:
        ax.axvline(sp, color="red", linestyle="--", label=f"saturation @ C={sp}")
        ax.legend()
    ax.set(xlabel="Concurrent clients", ylabel="Error rate (%)",
           title="Error rate vs concurrency")
    save(fig, "errorrate_vs_concurrency.png")

    series = pd.DataFrame(saturation["T2"]["series"])
    if not series.empty:
        fig, ax1 = plt.subplots(figsize=(7, 5))
        sns.lineplot(data=series, x="t_start_s", y="p95_ms",
                     marker="o", color="#2980b9", ax=ax1)
        ax1.set_ylabel("p95 latency (ms)", color="#2980b9")
        ax1.set_xlabel("Elapsed time (s)")
        ax2 = ax1.twinx()
        sns.lineplot(x=series["t_start_s"], y=series["error_rate"] * 100,
                     marker="s", color="#c0392b", ax=ax2)
        ax2.set_ylabel("error rate (%)", color="#c0392b")
        ax2.grid(False)
        drift = saturation["T2"]["drift_detected"]
        ax1.set_title(f"Sustained-load drift ({saturation['T2']['preset']}) -- "
                      f"{'DRIFT (leak?)' if drift else 'stable'}")
        save(fig, "load_drift.png")


def parse_args(argv=None):
    p = argparse.ArgumentParser(description="Paxos saturation / performance harness")
    p.add_argument("--config", choices=["clean", "chaos"], default="chaos")
    p.add_argument("--sweep", default="1,2,4,8,16,32,64")
    p.add_argument("--writers", type=int, default=6)
    p.add_argument("--reads", type=int, default=12)
    p.add_argument("--repeats", type=int, default=6)
    p.add_argument("--drift-seconds", type=int, default=120)
    p.add_argument("--cluster-size", type=int, default=0)
    p.add_argument("--cluster-name", default="saturation")
    p.add_argument("--no-manage-cluster", action="store_true")
    p.add_argument("--keep-up", action="store_true")
    p.add_argument("--no-figures", action="store_true")
    args = p.parse_args(argv)
    args.sweep = [int(x) for x in str(args.sweep).split(",") if x.strip()]
    return args


def main(argv=None) -> int:
    args = parse_args(argv)
    cfg = Config.load(cli_cluster_size=args.cluster_size, cluster_name=args.cluster_name)
    cluster = PaxosCluster(cfg)
    client = PaxosClient(cfg)
    manage = not args.no_manage_cluster

    if args.config != "chaos":
        print("NOTE: the T2 thread-leak test only bites under --config chaos.",
              file=sys.stderr)

    saturation = None
    try:
        if manage:
            cluster.bring_up(args.config)
        else:
            print(f"Using already-up cluster ({cfg.cluster_size} nodes assumed).")
        saturation = SaturationSuite(client, cfg.cluster_size).run(args, args.config)
    finally:
        metrics_dir = Path(cfg.repo_root) / "metrics" / cfg.cluster_name
        if saturation is not None:
            metrics_dir.mkdir(parents=True, exist_ok=True)
            (metrics_dir / "saturation_results.json").write_text(
                json.dumps({"config": args.config, "cluster_size": cfg.cluster_size,
                            "results": saturation}, indent=2, default=str))
        if manage and not args.keep_up:
            cluster.tear_down()

    if saturation is not None and not args.no_figures:
        figures(saturation, cfg.repo_root, cfg.cluster_name)
    return 0


if __name__ == "__main__":
    sys.exit(main())