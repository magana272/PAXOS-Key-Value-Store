#!/usr/bin/env python3
"""Shared harness for the Paxos correctness validators.

Extracted verbatim from the old paxos_validate.py: the Docker-driving client,
the cluster lifecycle, the CSV op model, and the small stats helpers. The
per-property validators in this package import from here so each correctness
check is its own named module while the infrastructure lives in one place.
"""
from __future__ import annotations

import hashlib
import io
import os
import re
import statistics
import subprocess
import time
from collections import Counter
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import asdict, dataclass, field
from pathlib import Path

import pandas as pd
from dotenv import load_dotenv

REPO_ROOT = Path(__file__).resolve().parent.parent
load_dotenv(REPO_ROOT / ".env")

MISSING_SENTINEL = "KEY does not exist"
PUT_ACK = "KEY Value Successfully Set"
DELETE_ACK = "Key-Value Successfully Deleted"

EMPTY_SUMMARY = {"ops": 0, "ok": 0, "error_rate": 0.0,
                 "p50_ms": None, "p95_ms": None, "p99_ms": None}


def hash12(s: str) -> str:
    return hashlib.sha1((s or "").encode("utf-8")).hexdigest()[:12]


def slope(xs, ys) -> float:
    df = pd.DataFrame({"x": xs, "y": ys}).dropna()
    if len(df) < 2 or df.x.nunique() < 2:
        return 0.0
    return statistics.linear_regression(df.x, df.y).slope


@dataclass
class OpResult:
    phase: str
    op: str
    key: str
    success: bool
    match: bool
    latency_ms: int | None
    error: str
    resp_hash: str

    @classmethod
    def from_csv_row(cls, d: dict) -> "OpResult":
        latency = pd.to_numeric(d.get("latency_ms"), errors="coerce")
        return cls(
            phase=d.get("phase", ""),
            op=d.get("op", ""),
            key=d.get("key", ""),
            success=str(d.get("success", "")).lower() == "true",
            match=str(d.get("match", "")).lower() == "true",
            latency_ms=None if pd.isna(latency) else int(latency),
            error=str(d.get("error") or "").strip('"'),
            resp_hash=str(d.get("resp_hash", "") or ""),
        )

    @classmethod
    def failure(cls, op: str, key: str, error: str) -> "OpResult":
        return cls("op", op, key, False, False, None, error, "")


def summarize(results) -> dict:
    if not results:
        return dict(EMPTY_SUMMARY)
    df = pd.DataFrame(asdict(r) for r in results)
    ok = int(df.success.sum())
    lats = df.loc[df.success, "latency_ms"].dropna()
    q = (lats.quantile([0.5, 0.95, 0.99]) if not lats.empty
         else pd.Series([None] * 3, index=[0.5, 0.95, 0.99]))
    return {
        "ops": len(df),
        "ok": ok,
        "error_rate": 1.0 - ok / len(df),
        "p50_ms": None if q[0.5] is None else float(q[0.5]),
        "p95_ms": None if q[0.95] is None else float(q[0.95]),
        "p99_ms": None if q[0.99] is None else float(q[0.99]),
    }


def evaluate_single_winner(read_results, proposed_values) -> dict:
    """Given reads of one key and the set of values that were proposed for it,
    decide whether exactly one winner was chosen and whether it came from the
    proposed set. Shared by linearizability and retry_convergence."""
    hash_lookup = {hash12(v): v for v in proposed_values}
    successful = [r for r in read_results if r.success]
    hashes = set(Counter(r.resp_hash for r in successful))
    return {
        "reads_ok": len(successful),
        "distinct_values_observed": len(hashes),
        "single_winner": len(hashes) == 1,
        "from_proposed": hashes <= hash_lookup.keys(),
        "inconclusive": not successful,
        "winner": hash_lookup.get(next(iter(hashes), None)),
        "observed_values": [hash_lookup.get(h, f"<foreign:{h}>") for h in hashes],
    }


@dataclass
class Config:
    net: str
    image: str
    repo_root: Path = REPO_ROOT
    port: int = 1099
    cluster_size: int = 10
    presets: dict = field(default_factory=lambda: {
        "clean": ("0.0", "0.0", "0.0"),
        "chaos": ("0.1", "0.1", "0.1"),
    })

    @classmethod
    def load(cls, repo_root=REPO_ROOT, cli_cluster_size=0) -> "Config":
        cfg = cls(
            net=os.getenv("PAXOS_NET", "paxos-key-value-store_paxos_net"),
            image=os.getenv("PAXOS_IMAGE", "paxos-kvstore:latest"),
            repo_root=repo_root,
        )
        cfg.presets["chaos"] = (
            os.getenv("ACCEPT_FAIL", "0.1"),
            os.getenv("PROPOSE_FAIL", "0.1"),
            os.getenv("MSG_LOSS", "0.1"),
        )
        cfg.cluster_size = cls._effective_cluster_size(repo_root, cli_cluster_size)
        return cfg

    @staticmethod
    def _effective_cluster_size(repo_root, cli_value) -> int:
        if cli_value:
            return cli_value
        compose = Path(repo_root) / "docker-compose.yml"
        if compose.exists():
            ids = set(re.findall(r"^\s{2}(node\d+):", compose.read_text(), re.M))
            if ids:
                return len(ids)
        return int(os.getenv("CLUSTER_SIZE", 10))


class PaxosClient:
    def __init__(self, config: Config):
        self.cfg = config

    @staticmethod
    def node(i: int, cluster_size: int) -> dict:
        j = i % cluster_size
        return {"host": f"node{j}", "node_id": j}

    def run_op(self, host, node_id, op, key, value=None,
               repeats=1, failed_count=0, timeout=180) -> list:
        cmd = [
            "docker", "run", "--rm", "--network", self.cfg.net,
            "--entrypoint", "java", self.cfg.image,
            "-cp", "/app/KVStore2PC.jar", "manuel.rpckvstore.Validate",
            host, str(self.cfg.port), "op",
            str(failed_count), str(repeats), str(node_id), op, key,
        ]
        if value is not None:
            cmd.append(value)
        try:
            proc = subprocess.run(cmd, cwd=self.cfg.repo_root, capture_output=True,
                                  text=True, timeout=timeout, check=False)
        except subprocess.TimeoutExpired:
            return [OpResult.failure(op, key, "docker-timeout")]
        if not proc.stdout.strip():
            return [OpResult.failure(op, key, f"no-output: {proc.stderr.strip()[:200]}")]
        df = pd.read_csv(io.StringIO(proc.stdout))
        return [OpResult.from_csv_row(row) for row in df.to_dict("records")]

    def run_many(self, specs, max_workers) -> list:
        if not specs:
            return []
        with ThreadPoolExecutor(max_workers=max_workers) as pool:
            futures = [pool.submit(self.run_op, **spec) for spec in specs]
            return [row for f in as_completed(futures) for row in f.result()]


class PaxosCluster:
    def __init__(self, config: Config):
        self.cfg = config

    def _sh(self, cmd, timeout=None, check=False, env=None, stdout=None):
        return subprocess.run(cmd, cwd=self.cfg.repo_root, capture_output=stdout is None,
                              stdout=stdout, text=True, timeout=timeout, check=check, env=env)

    def ensure_compose_exists(self):
        compose = Path(self.cfg.repo_root) / "docker-compose.yml"
        if compose.exists():
            return
        print("Generating docker-compose.yml...")
        with compose.open("w") as f:
            self._sh(["bash", "scripts/gen-compose.sh"], check=True, stdout=f)

    def bring_up(self, preset):
        accept, propose, loss = self.cfg.presets[preset]
        print(f"===== Starting {preset} cluster =====")
        env = {**os.environ, "ACCEPT_FAIL": accept,
               "PROPOSE_FAIL": propose, "MSG_LOSS": loss}
        services = [f"node{i}" for i in range(self.cfg.cluster_size)]
        self.ensure_compose_exists()
        self._sh(["docker", "compose", "up", "-d", "--build", *services],
                 timeout=900, check=True, env=env)
        self.wait_ready()

    def wait_ready(self, attempts=90):
        expected = self.cfg.cluster_size
        for _ in range(attempts):
            logs = self._sh(["docker", "compose", "logs", "--no-color"]).stdout
            if logs.count("Successfully joined the Paxos network!") >= expected:
                print("Cluster Ready")
                time.sleep(2)
                return
            time.sleep(1)
        raise RuntimeError("Cluster failed to form.")

    def stop_node(self, name):
        """Stop one node container (quorum_tolerance kill sweep)."""
        self._sh(["docker", "compose", "stop", name], timeout=120)

    def start_node(self, name):
        """Restart a previously stopped node container."""
        self._sh(["docker", "compose", "start", name], timeout=120)

    def tear_down(self):
        print("===== Stopping cluster =====")
        self._sh(["docker", "compose", "down", "-v", "--remove-orphans"], timeout=120)

    def logs(self) -> str:
        return self._sh(["docker", "compose", "logs", "--no-color"]).stdout