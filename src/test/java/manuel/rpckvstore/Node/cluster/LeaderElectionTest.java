package manuel.rpckvstore.Node.cluster;

import manuel.rpckvstore.NodeAddress;
import manuel.rpckvstore.Node.BaseServer;
import manuel.rpckvstore.testutil.FakeNode;
import manuel.rpckvstore.testutil.RmiHarness;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class LeaderElectionTest {

    @Test
    void electsHighestNumericId() {
        PeerDirectory peers = new PeerDirectory(new NodeAddress("1", "h", "1"));
        peers.add(new NodeAddress("7", "h", "2"));
        peers.add(new NodeAddress("3", "h", "3"));

        LeaderElection election = new LeaderElection(peers);
        BaseServer leader = election.elect();
        assertEquals("7", election.getLeaderAddress().getId());
        assertEquals(leader, election.current());
    }

    @Test
    void electReturnsNullWhenNoPeers() {
        NodeAddress self = new NodeAddress("1", "h", "1");
        PeerDirectory peers = new PeerDirectory(self);
        peers.remove(self);

        LeaderElection election = new LeaderElection(peers);

        assertNull(election.elect());
        assertNull(election.getLeaderAddress());
    }

    @Test
    void currentTriggersElectionWhenUnset() throws Exception {
        try (RmiHarness harness = new RmiHarness()) {
            NodeAddress addr = harness.bind("4", new FakeNode());
            PeerDirectory peers = new PeerDirectory(addr);

            LeaderElection election = new LeaderElection(peers);

            assertNotNull(election.current());
            assertEquals("4", election.getLeaderAddress().getId());
        }
    }

    @Test
    void electResolvesReachableLeaderStub() throws Exception {
        try (RmiHarness harness = new RmiHarness()) {
            NodeAddress low = harness.bind("2", new FakeNode());
            NodeAddress high = harness.bind("6", new FakeNode());
            PeerDirectory peers = new PeerDirectory(low);
            peers.add(high);

            LeaderElection election = new LeaderElection(peers);
            BaseServer leader = election.elect();

            assertNotNull(leader);
            assertEquals("6", election.getLeaderAddress().getId());
            assertSame(leader, election.current());
        }
    }

    @Test
    void demoteClearsCurrentLeader() {
        PeerDirectory peers = new PeerDirectory(new NodeAddress("9", "h", "1"));
        LeaderElection election = new LeaderElection(peers);
        election.elect();

        election.demote();

        assertNull(election.current());
    }
}
