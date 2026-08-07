#!/usr/bin/env python3
"""
Aggregates the CSVs produced by scripts/validate.sh (metrics/*.csv) into
figures under img/. The harness runs two conditions per trial: loss0 (clean,
MSG_LOSS=0) and loss1 (real per-message loss on the Paxos RPCs).

  img/quorum_tolerance.png - PUT success rate vs. nodes killed (loss0 fixed-N
                              quorum sweep), with the theoretical majority
                              threshold marked. Only writes go through Paxos, so
                              only writes reveal the quorum boundary; the sweep
                              keeps the leader alive and kills middle nodes, so
                              the quorum stays N and the observed step lands at
                              floor((N-1)/2) failures tolerated.
  img/latency_boxplot.png   - PUT latency distribution per failure count
                              (loss0 sweep).
  img/throughput.png        - successful ops/sec, loss0 vs loss1, mean +/- std
                              across trials. Throughput is derived in-process
                              from per-op latencies (n_ok / total wall seconds),
                              so it excludes container/JVM startup entirely.
  img/latency_loss.png      - per-operation (PUT/GET/DELETE) load-phase latency
                              distribution, loss0 vs loss1.
  img/retries.png           - mean Paxos retries per trial, loss0 vs loss1.

Also prints a text summary and writes metrics/summary.txt.

Usage: python3 scripts/generate_charts.py [--cluster-size N] [--metrics-dir DIR] [--img-dir DIR]
"""
import argparse
import glob
import os
import re
import sys

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import Patch
import numpy as np
import pandas as pd

# Order load-phase ops are reported/plotted in.
_OP_ORDER = ["PUT", "GET", "DELETE"]

# trial<i>_loss<L>_...
_NAME_RE = re.compile(r"trial(?P<trial>\d+)_loss(?P<loss>\d+)_")


def _parse_name(path: str):
    m = _NAME_RE.search(os.path.basename(path))
    if not m:
        return None, None
    return m.group("trial"), m.group("loss")


def _load_csv_frames(metrics_dir: str, suffix: str) -> pd.DataFrame:
    paths = sorted(glob.glob(os.path.join(metrics_dir, f"trial*_loss*_{suffix}.csv")))
    frames = []
    for p in paths:
        trial, loss = _parse_name(p)
        if trial is None:
            continue
        try:
            df = pd.read_csv(p)
        except Exception as e:
            print(f"WARN: could not parse {p}: {e}", file=sys.stderr)
            continue
        if df.empty:
            continue
        df["trial"] = trial
        df["loss"] = loss
        frames.append(df)
    return pd.concat(frames, ignore_index=True) if frames else pd.DataFrame()


def load_retries(metrics_dir: str) -> pd.DataFrame:
    paths = sorted(glob.glob(os.path.join(metrics_dir, "trial*_loss*_retries.txt")))
    rows = []
    for p in paths:
        trial, loss = _parse_name(p)
        if trial is None:
            continue
        try:
            with open(p) as f:
                count = int((f.read().strip() or "0"))
        except Exception as e:
            print(f"WARN: could not parse {p}: {e}", file=sys.stderr)
            continue
        rows.append({"trial": trial, "loss": loss, "retries": count})
    return pd.DataFrame(rows)


def _throughput_per_trial(load_df: pd.DataFrame) -> pd.DataFrame:
    rows = []
    for (trial, loss), g in load_df.groupby(["trial", "loss"]):
        n_ok = int(g["success"].sum())
        total_ms = float(g["latency_ms"].sum())
        ops_per_sec = n_ok / (total_ms / 1000.0) if total_ms > 0 else 0.0
        rows.append({"trial": trial, "loss": loss, "ops_per_sec": ops_per_sec,
                     "fail_rate": 1.0 - g["success"].mean()})
    return pd.DataFrame(rows)


def save_fig(fig, name, img_dir):
    fig.tight_layout()
    fig.savefig(os.path.join(img_dir, name), dpi=150)
    plt.close(fig)
    print(f"Wrote {img_dir}/{name}")


def loss_label(loss, msg_loss_label):
    return "0% loss (baseline)" if loss == "0" else msg_loss_label


def std0(series):
    """Sample std, but 0 (not NaN) for a single-element group."""
    s = series.std()
    return 0 if pd.isna(s) else s


def _boxplot(ax, groups, labels):
    try:
        ax.boxplot(groups, tick_labels=labels, showmeans=True)  # matplotlib >= 3.9
    except TypeError:
        ax.boxplot(groups, labels=labels, showmeans=True)       # older matplotlib


def plot_quorum_tolerance(sweep_df: pd.DataFrame, cluster_size: int, img_dir: str):
    if sweep_df.empty:
        print("No sweep data found; skipping quorum_tolerance.png")
        return None

    # Only the write path runs Paxos, so only PUT reveals the quorum boundary.
    writes = sweep_df[sweep_df["op"] == "PUT"].copy()
    if writes.empty:
        print("No PUT rows in sweep data; skipping quorum_tolerance.png")
        return None
    writes["ok"] = writes["success"]
    per_trial = writes.groupby(["trial", "failed_count"])["ok"].mean().reset_index()
    agg = per_trial.groupby("failed_count")["ok"].agg(["mean", "std", "count"]).reset_index()
    agg["std"] = agg["std"].fillna(0)

    strict_majority = cluster_size // 2 + 1
    max_tolerated = cluster_size - strict_majority

    fig, ax = plt.subplots(figsize=(8, 5))
    ax.errorbar(agg["failed_count"], agg["mean"] * 100, yerr=agg["std"] * 100,
                marker="o", capsize=4, label="Observed success rate")
    ax.axvline(max_tolerated + 0.5, color="red", linestyle="--",
               label=f"Theoretical limit: {max_tolerated} failures tolerated\n(majority = {strict_majority}/{cluster_size} nodes)")
    ax.set_xlabel("Nodes killed (leader + node0 always survive)")
    ax.set_ylabel("PUT success rate (%)")
    ax.set_title(f"Quorum tolerance under node failures (n={cluster_size} cluster)")
    ax.set_ylim(-5, 105)
    ax.legend(loc="lower left")
    ax.grid(alpha=0.3)
    save_fig(fig, "quorum_tolerance.png", img_dir)
    return agg, max_tolerated


def plot_latency_boxplot(sweep_df: pd.DataFrame, img_dir: str):
    if sweep_df.empty:
        print("No sweep data found; skipping latency_boxplot.png")
        return
    ok = sweep_df[(sweep_df["op"] == "PUT") & sweep_df["success"]]
    if ok.empty:
        print("No successful PUTs found; skipping latency_boxplot.png")
        return

    failed_counts = sorted(ok["failed_count"].unique())
    grouped = [ok[ok["failed_count"] == f]["latency_ms"].values for f in failed_counts]
    labels = [f"{f}\n(N={len(g)})" for f, g in zip(failed_counts, grouped)]

    fig, ax = plt.subplots(figsize=(8, 5))
    _boxplot(ax, grouped, labels)
    ax.set_xlabel("Nodes killed")
    ax.set_ylabel("PUT latency (ms)")
    ax.set_title("Time-to-consensus for writes under increasing failures")
    ax.grid(alpha=0.3, axis="y")

    all_latencies = ok["latency_ms"].values
    whisker_tops = []
    for g in grouped:
        g = np.asarray(g, dtype=float)
        q25, q75 = np.percentile(g, [25, 75])
        iqr = q75 - q25
        upper = g[g <= q75 + 1.5 * iqr]
        whisker_tops.append(upper.max() if upper.size else q75)
    y_cap = max(max(whisker_tops) * 1.15, 10)
    n_clipped = int((all_latencies > y_cap).sum())
    if n_clipped > 0:
        ax.set_ylim(0, y_cap)
        ax.text(0.99, 0.98,
                f"{n_clipped} outlier(s) >{y_cap:.0f} ms clipped",
                transform=ax.transAxes, ha="right", va="top", fontsize=8,
                color="gray", style="italic")

    save_fig(fig, "latency_boxplot.png", img_dir)


def plot_throughput(tput_df: pd.DataFrame, msg_loss_label: str, img_dir: str):
    if tput_df.empty:
        print("No load data found; skipping throughput.png")
        return None

    losses = sorted(tput_df["loss"].unique())
    means, stds, labels = [], [], []
    for loss in losses:
        g = tput_df[tput_df["loss"] == loss]["ops_per_sec"]
        means.append(g.mean())
        stds.append(std0(g))
        labels.append(loss_label(loss, msg_loss_label))

    fig, ax = plt.subplots(figsize=(6, 5))
    ax.bar(labels, means, yerr=stds, capsize=6)
    ax.set_ylabel("Throughput (successful ops/sec)")
    ax.set_title(f"Sustained throughput, mean of {tput_df['trial'].nunique()} trials")
    ax.grid(alpha=0.3, axis="y")
    save_fig(fig, "throughput.png", img_dir)
    return dict(zip(losses, means))


def plot_latency_loss(load_df: pd.DataFrame, msg_loss_label: str, img_dir: str):
    if load_df.empty:
        print("No load data found; skipping latency_loss.png")
        return
    ok = load_df[load_df["success"]]
    if ok.empty:
        print("No successful load ops; skipping latency_loss.png")
        return

    present = set(ok["op"].unique())
    ops = [o for o in _OP_ORDER if o in present]
    losses = sorted(ok["loss"].unique())
    if not ops:
        print("No recognized load ops; skipping latency_loss.png")
        return

    # One facet per op (PUT/GET/DELETE); within each facet, one box per loss
    # condition so message-loss cost is visible per operation.
    colors = plt.cm.tab10.colors
    fig, axes = plt.subplots(1, len(ops), figsize=(3 + 2.5 * len(ops), 5),
                             sharey=False, squeeze=False)
    axes = axes[0]
    legend = {}
    for oi, op in enumerate(ops):
        ax = axes[oi]
        groups, positions, box_colors = [], [], []
        for li, loss in enumerate(losses):
            vals = ok[(ok["op"] == op) & (ok["loss"] == loss)]["latency_ms"].values
            if len(vals) == 0:
                continue
            groups.append(vals)
            positions.append(li)
            box_colors.append(colors[li % len(colors)])
            legend[loss] = Patch(facecolor=colors[li % len(colors)], alpha=0.5,
                                 label=loss_label(loss, msg_loss_label))
        if groups:
            bp = ax.boxplot(groups, positions=positions, widths=0.6,
                            showmeans=True, patch_artist=True)
            for box, color in zip(bp["boxes"], box_colors):
                box.set(facecolor=color, alpha=0.5)
            for med in bp["medians"]:
                med.set(color="black")
        ax.set_title(op)
        ax.set_xticks(range(len(losses)))
        ax.set_xticklabels([loss_label(l, msg_loss_label) for l in losses],
                           rotation=20, ha="right", fontsize=8)
        ax.grid(alpha=0.3, axis="y")
        ax.set_ylabel("Load-phase op latency (ms)")

    fig.suptitle("Per-operation latency: PUT vs GET vs DELETE")
    fig.legend(handles=[legend[l] for l in losses if l in legend],
               loc="lower center", ncol=len(legend))
    fig.tight_layout(rect=(0, 0.06, 1, 1))
    fig.savefig(os.path.join(img_dir, "latency_loss.png"), dpi=150)
    plt.close(fig)
    print(f"Wrote {img_dir}/latency_loss.png")


def plot_retries(retries_df: pd.DataFrame, msg_loss_label: str, img_dir: str):
    if retries_df.empty:
        print("No retries data found; skipping retries.png")
        return None
    agg = retries_df.groupby("loss")["retries"].agg(["mean", "std"]).reset_index()
    agg["std"] = agg["std"].fillna(0)
    labels = [loss_label(loss, msg_loss_label) for loss in agg["loss"]]

    fig, ax = plt.subplots(figsize=(6, 5))
    ax.bar(labels, agg["mean"], yerr=agg["std"], capsize=6, color="#8e44ad")
    ax.set_ylabel("Paxos retries per trial (mean)")
    ax.set_title("Consensus retries induced by message loss")
    ax.grid(alpha=0.3, axis="y")
    save_fig(fig, "retries.png", img_dir)
    return dict(zip(agg["loss"], agg["mean"]))


def write_summary(sweep_df, load_df, tput_df, retries_df, cluster_size,
                  max_tolerated, msg_loss_label, metrics_dir):
    lines = []
    lines.append(f"Cluster size: {cluster_size}")
    lines.append(f"Theoretical max tolerated failures (majority quorum): {max_tolerated}")

    # --- zero-data-loss: every successful GET must return the exact PUT value ---
    all_reads = []
    if not sweep_df.empty:
        all_reads.append(sweep_df[sweep_df["op"] == "GET"])
    if not load_df.empty:
        all_reads.append(load_df[load_df["op"] == "GET"])
    if all_reads:
        reads = pd.concat(all_reads, ignore_index=True)
        mismatches = reads[reads["success"] & ~reads["match"]]
        lines.append(f"Successful GETs with value mismatch (data loss; should be 0): {len(mismatches)}")

    # Quorum tolerance is a property of the WRITE path (only PUT/DELETE run Paxos).
    if not sweep_df.empty:
        puts = sweep_df[sweep_df["op"] == "PUT"].copy()
        if not puts.empty:
            per_failed = puts.groupby("failed_count")["success"].mean() * 100
            empirical_max = 0
            for f, rate in per_failed.items():
                if rate >= 99.9:
                    empirical_max = max(empirical_max, int(f))
            lines.append(f"Empirically observed max tolerated failures (PUT >=99.9% success): {empirical_max}")

    if not load_df.empty:
        lines.append("")
        lines.append("Load phase (per condition, latency split by op):")
        for loss in sorted(load_df["loss"].unique()):
            g = load_df[load_df["loss"] == loss]
            ok = g[g["success"]]
            cond = loss_label(loss, msg_loss_label)
            fail_rate = (1.0 - g["success"].mean()) * 100
            lines.append(f"  {cond}: failure rate {fail_rate:.1f}%")
            for op in [o for o in _OP_ORDER if o in set(g["op"].unique())]:
                lat = ok[ok["op"] == op]["latency_ms"]
                lat_txt = (f"avg {lat.mean():.1f} ms, p95 {lat.quantile(0.95):.1f} ms (n={len(lat)})"
                           if not lat.empty else "no successful ops")
                lines.append(f"    {op}: {lat_txt}")

    if not tput_df.empty:
        for loss in sorted(tput_df["loss"].unique()):
            g = tput_df[tput_df["loss"] == loss]["ops_per_sec"]
            cond = loss_label(loss, msg_loss_label)
            std = std0(g)
            lines.append(f"Throughput ({cond}): {g.mean():.2f} +/- {std:.2f} ops/sec "
                         f"(n={len(g)} trials)")

    if not retries_df.empty:
        for loss in sorted(retries_df["loss"].unique()):
            g = retries_df[retries_df["loss"] == loss]["retries"]
            cond = loss_label(loss, msg_loss_label)
            lines.append(f"Paxos retries ({cond}): {g.mean():.1f} per trial (mean)")

    summary = "\n".join(lines)
    print("\n=== SUMMARY ===")
    print(summary)
    with open(os.path.join(metrics_dir, "summary.txt"), "w") as f:
        f.write(summary + "\n")
    print(f"\nWrote {metrics_dir}/summary.txt")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--cluster-size", type=int, default=int(os.environ.get("CLUSTER_SIZE", 10)))
    parser.add_argument("--metrics-dir", default="metrics")
    parser.add_argument("--img-dir", default="img")
    parser.add_argument("--loss-label", default=None,
                        help="Legend label for the lossy condition (default derived from MSG_LOSS)")
    args = parser.parse_args()

    os.makedirs(args.metrics_dir, exist_ok=True)
    os.makedirs(args.img_dir, exist_ok=True)

    msg_loss = os.environ.get("MSG_LOSS_RATE", os.environ.get("MSG_LOSS", "0.1"))
    try:
        loss_pct = f"{float(msg_loss) * 100:.0f}% loss"
    except ValueError:
        loss_pct = "message loss"
    msg_loss_label = args.loss_label or loss_pct

    # write-path quorum sweeps only run in the clean (loss0) condition
    sweep_df = _load_csv_frames(args.metrics_dir, "sweep_f*")
    load_df = _load_csv_frames(args.metrics_dir, "load")
    retries_df = load_retries(args.metrics_dir)
    tput_df = _throughput_per_trial(load_df) if not load_df.empty else pd.DataFrame()

    result = plot_quorum_tolerance(sweep_df, args.cluster_size, args.img_dir)
    max_tolerated = result[1] if result else args.cluster_size - (args.cluster_size // 2 + 1)
    plot_latency_boxplot(sweep_df, args.img_dir)
    plot_throughput(tput_df, msg_loss_label, args.img_dir)
    plot_latency_loss(load_df, msg_loss_label, args.img_dir)
    plot_retries(retries_df, msg_loss_label, args.img_dir)
    write_summary(sweep_df, load_df, tput_df, retries_df, args.cluster_size,
                  max_tolerated, msg_loss_label, args.metrics_dir)


if __name__ == "__main__":
    main()
