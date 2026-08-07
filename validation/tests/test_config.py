import sys
from pathlib import Path

import pytest

pytest.importorskip("pandas")
pytest.importorskip("dotenv")

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from validation._harness import Config


def test_net_and_compose_file_derived_from_cluster_name(monkeypatch):
    monkeypatch.delenv("PAXOS_NET", raising=False)
    cfg = Config.load(cluster_name="foo")
    assert cfg.cluster_name == "foo"
    assert cfg.net == "foo_paxos_net"
    assert cfg.compose_file == "docker-compose.foo.yml"


def test_paxos_net_env_overrides_derivation(monkeypatch):
    monkeypatch.setenv("PAXOS_NET", "custom_net")
    cfg = Config.load(cluster_name="bar")
    assert cfg.net == "custom_net"
    assert cfg.compose_file == "docker-compose.bar.yml"


def test_default_cluster_name(monkeypatch):
    monkeypatch.delenv("PAXOS_NET", raising=False)
    cfg = Config.load()
    assert cfg.cluster_name == "default"
    assert cfg.net == "default_paxos_net"
    assert cfg.compose_file == "docker-compose.default.yml"
