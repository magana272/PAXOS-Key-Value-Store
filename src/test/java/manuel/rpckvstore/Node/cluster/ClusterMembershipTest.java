package manuel.rpckvstore.Node.cluster;

import manuel.rpckvstore.Node.Response;
import manuel.rpckvstore.NodeAddress;
import manuel.rpckvstore.testutil.FakeNode;
import manuel.rpckvstore.testutil.RmiHarness;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ClusterMembershipTest {

    private static ClusterMembership membership(PeerDirectory peers, RmiTransport transport,
                                                String initialIp, String initialPort) {
        return new ClusterMembership("1", () -> "127.0.0.1", 6000,
                initialIp, initialPort, peers, transport);
    }

    @Test
    void joinAddsPeerAndReturnsJoinedResponse() {
        PeerDirectory peers = new PeerDirectory(new NodeAddress("1", "127.0.0.1", "6000"));
        ClusterMembership membership =
                membership(peers, new RmiTransport(), "127.0.0.1", "1099");

        Response response = membership.join("2", "127.0.0.1",
                String.valueOf(RmiHarness.freePort()));

        assertEquals("Joined", response.header);
        assertEquals(2, peers.size());
    }

    @Test
    void informOfNewNodeSkipsSelfAndInformsReachablePeer() throws Exception {
        try (RmiHarness harness = new RmiHarness()) {
            FakeNode peer = new FakeNode();
            NodeAddress peerAddr = harness.bind("2", peer);
            PeerDirectory peers = new PeerDirectory(new NodeAddress("1", "127.0.0.1", "6000"));
            peers.add(peerAddr);
            ClusterMembership membership =
                    membership(peers, new RmiTransport(), "127.0.0.1", "1099");

            membership.informOfNewNode();

            assertEquals(1, peer.informCalls.get());
        }
    }

    @Test
    void informOfNewNodeSwallowsUnreachablePeer() {
        PeerDirectory peers = new PeerDirectory(new NodeAddress("1", "127.0.0.1", "6000"));
        peers.add(new NodeAddress("2", "127.0.0.1", String.valueOf(RmiHarness.freePort())));
        ClusterMembership membership =
                membership(peers, new RmiTransport(), "127.0.0.1", "1099");

        assertDoesNotThrow(membership::informOfNewNode);
    }

    @Test
    void acceptUpdatedMembershipReplacesPeers() {
        PeerDirectory peers = new PeerDirectory(new NodeAddress("1", "127.0.0.1", "6000"));
        ClusterMembership membership =
                membership(peers, new RmiTransport(), "127.0.0.1", "1099");
        Set<NodeAddress> updated = new HashSet<>();
        updated.add(new NodeAddress("8", "127.0.0.1", "6000"));
        updated.add(new NodeAddress("9", "127.0.0.1", "6000"));

        membership.acceptUpdatedMembership(updated);

        assertEquals(2, peers.size());
    }

    @Test
    void connectToInitialNodeJoinsThroughRegistry() throws Exception {
        try (RmiHarness harness = new RmiHarness()) {
            harness.bind("0", new FakeNode());
            PeerDirectory peers = new PeerDirectory(new NodeAddress("1", "127.0.0.1", "6000"));
            ClusterMembership membership = membership(peers, new RmiTransport(),
                    "127.0.0.1", String.valueOf(harness.port()));

            assertDoesNotThrow(membership::connectToInitialNode);
        }
    }

    @Test
    void connectToInitialNodeThrowsWhenInitialNodeAbsent() {
        PeerDirectory peers = new PeerDirectory(new NodeAddress("1", "127.0.0.1", "6000"));
        ClusterMembership membership = membership(peers, new RmiTransport(),
                "127.0.0.1", String.valueOf(RmiHarness.freePort()));

        assertThrows(RuntimeException.class, membership::connectToInitialNode);
    }
}