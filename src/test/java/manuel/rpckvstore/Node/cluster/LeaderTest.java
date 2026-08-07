package manuel.rpckvstore.Node.cluster;

import manuel.rpckvstore.Node.Response;
import manuel.rpckvstore.Node.Learner.KeyValueStore;
import manuel.rpckvstore.NodeAddress;
import manuel.rpckvstore.Packet.Packet;
import manuel.rpckvstore.Packet.TransactionPacket;
import manuel.rpckvstore.Packet.Vote;
import manuel.rpckvstore.testutil.FakeNode;
import manuel.rpckvstore.testutil.RmiHarness;
import org.junit.jupiter.api.Test;

import java.rmi.RemoteException;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LeaderTest {

    private static Packet put(String k, String v) {
        return new Packet("{\"TYPE\":\"PUT\",\"KEY\":\"" + k + "\",\"VALUE\":\"" + v + "\"}");
    }

    private static Packet get(String k) {
        return new Packet("{\"TYPE\":\"GET\",\"KEY\":\"" + k + "\"}");
    }

    private static ClusterMembership membershipFor(PeerDirectory peers) {
        return new ClusterMembership("1", () -> "127.0.0.1", 6000,
                "127.0.0.1", "1099", peers, new RmiTransport());
    }

    @Test
    void pinnedSubmitForwardsToPinnedLeader() throws Exception {
        FakeNode pinnedStub = new FakeNode();
        Leader leader = Leader.pinned(pinnedStub);

        Response r = leader.submit(new TransactionPacket(put("k", "v"), Vote.YES));

        assertNotNull(r);
        assertEquals("v", pinnedStub.store().Get("k"));
    }

    @Test
    void pinnedReadForwardsToPinnedLeader() throws Exception {
        FakeNode pinnedStub = new FakeNode();
        pinnedStub.store().Put("k", "remote");
        Leader leader = Leader.pinned(pinnedStub);

        assertEquals("remote", leader.read("1", get("k")).body);
    }

    @Test
    void standaloneReadAppliesLocally() throws Exception {
        PeerDirectory peers = new PeerDirectory(new NodeAddress("1", "127.0.0.1", "6000"));
        KeyValueStore kv = new KeyValueStore();
        kv.Put("k", "local");
        Leader leader = new Leader(peers, membershipFor(peers), kv);

        assertEquals("local", leader.read("1", get("k")).body);
    }

    @Test
    void readAsElectedLeaderAppliesLocally() throws Exception {
        NodeAddress self = new NodeAddress("9", "127.0.0.1", "6000");
        PeerDirectory peers = new PeerDirectory(self);
        peers.add(new NodeAddress("2", "127.0.0.1", "6001"));
        KeyValueStore kv = new KeyValueStore();
        kv.Put("k", "leaderlocal");
        Leader leader = new Leader(peers, membershipFor(peers), kv);

        assertEquals("leaderlocal", leader.read("9", get("k")).body);
    }

    @Test
    void readForwardsToRemoteLeader() throws Exception {
        try (RmiHarness harness = new RmiHarness()) {
            FakeNode leaderStub = new FakeNode();
            leaderStub.store().Put("k", "fromleader");
            NodeAddress self = harness.bind("2", new FakeNode());
            NodeAddress leaderAddr = harness.bind("6", leaderStub);
            PeerDirectory peers = new PeerDirectory(self);
            peers.add(leaderAddr);
            Leader leader = new Leader(peers, membershipFor(peers), new KeyValueStore());

            assertEquals("fromleader", leader.read("2", get("k")).body);
        }
    }

    @Test
    void submitForwardsToHealthyLeader() throws Exception {
        try (RmiHarness harness = new RmiHarness()) {
            FakeNode leaderStub = new FakeNode();
            NodeAddress self = harness.bind("2", new FakeNode());
            NodeAddress leaderAddr = harness.bind("6", leaderStub);
            PeerDirectory peers = new PeerDirectory(self);
            peers.add(leaderAddr);
            Leader leader = new Leader(peers, membershipFor(peers), new KeyValueStore());

            leader.submit(new TransactionPacket(put("k", "v"), Vote.YES));

            assertEquals("v", leaderStub.store().Get("k"));
        }
    }

    @Test
    void submitDemotesDeadLeaderThenFailsOver() throws Exception {
        try (RmiHarness harness = new RmiHarness()) {
            FakeNode deadLeader = new FakeNode().alive(false);
            FakeNode liveBackup = new FakeNode();
            NodeAddress backupAddr = harness.bind("3", liveBackup);
            NodeAddress deadAddr = harness.bind("7", deadLeader);
            PeerDirectory peers = new PeerDirectory(backupAddr);
            peers.add(deadAddr);
            Leader leader = new Leader(peers, membershipFor(peers), new KeyValueStore());

            leader.submit(new TransactionPacket(put("k", "v"), Vote.YES));

            assertEquals("v", liveBackup.store().Get("k"));
        }
    }

    @Test
    void submitThrowsWhenNoLeaderCanBeElected() {
        NodeAddress self = new NodeAddress("1", "127.0.0.1", "6000");
        PeerDirectory peers = new PeerDirectory(self);
        peers.remove(self);
        Leader leader = new Leader(peers, membershipFor(peers), new KeyValueStore());

        assertThrows(RemoteException.class,
                () -> leader.submit(new TransactionPacket(put("k", "v"), Vote.YES)));
    }

    @Test
    void submitThrowsWhenNoLeaderAfterFailover() throws Exception {
        try (RmiHarness harness = new RmiHarness()) {
            FakeNode deadLeader = new FakeNode().alive(false);
            NodeAddress deadAddr = harness.bind("4", deadLeader);
            PeerDirectory peers = new PeerDirectory(deadAddr);
            Leader leader = new Leader(peers, membershipFor(peers), new KeyValueStore());

            assertThrows(RemoteException.class,
                    () -> leader.submit(new TransactionPacket(put("k", "v"), Vote.YES)));
        }
    }

    @Test
    void currentAndAddressAndRunElectionResolveLeader() throws Exception {
        try (RmiHarness harness = new RmiHarness()) {
            NodeAddress addr = harness.bind("5", new FakeNode());
            PeerDirectory peers = new PeerDirectory(addr);
            Leader leader = new Leader(peers, membershipFor(peers), new KeyValueStore());

            leader.runElection();

            assertNotNull(leader.current());
            assertEquals("5", leader.address().getId());
        }
    }

    @Test
    void twoArgConstructorUsesFreshStore() throws Exception {
        PeerDirectory peers = new PeerDirectory(new NodeAddress("1", "127.0.0.1", "6000"));
        Leader leader = new Leader(peers, membershipFor(peers));

        leader.read("1", put("k", "v"));

        assertEquals("v", leader.read("1", get("k")).body);
    }

    @Test
    void informDelegatesToMembership() throws Exception {
        PeerDirectory peers = new PeerDirectory(new NodeAddress("1", "127.0.0.1", "6000"));
        Leader leader = new Leader(peers, membershipFor(peers));
        Set<NodeAddress> updated = new HashSet<>();
        updated.add(new NodeAddress("2", "127.0.0.1", "6001"));

        leader.inform(updated);

        assertEquals(1, peers.size());
    }
}