package manuel.rpckvstore.Node.Proposer;

import manuel.rpckvstore.Node.Response;
import manuel.rpckvstore.Node.cluster.PeerDirectory;
import manuel.rpckvstore.Node.cluster.RmiTransport;
import manuel.rpckvstore.NodeAddress;
import manuel.rpckvstore.Packet.Packet;
import manuel.rpckvstore.Packet.TransactionPacket;
import manuel.rpckvstore.Packet.Vote;
import manuel.rpckvstore.testutil.FakeNode;
import manuel.rpckvstore.testutil.RmiHarness;
import org.junit.jupiter.api.Test;

import java.rmi.RemoteException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PaxosProposerRoundTest {

    private static Packet put(String k, String v) {
        return new Packet("{\"TYPE\":\"PUT\",\"KEY\":\"" + k + "\",\"VALUE\":\"" + v + "\"}");
    }

    private static Packet del(String k) {
        return new Packet("{\"TYPE\":\"DELETE\",\"KEY\":\"" + k + "\"}");
    }

    @Test
    void majorityRoundCommitsPutToAllReachablePeers() throws Exception {
        try (RmiHarness harness = new RmiHarness()) {
            FakeNode a = new FakeNode();
            FakeNode b = new FakeNode();
            FakeNode c = new FakeNode();
            PeerDirectory peers = new PeerDirectory(harness.bind("1", a));
            peers.add(harness.bind("2", b));
            peers.add(harness.bind("3", c));
            PaxosProposer proposer = new PaxosProposer("1", peers, new RmiTransport());

            Response r = proposer.propose(new TransactionPacket(put("k", "v"), Vote.YES));

            assertEquals("v", r.body);
            assertEquals("v", a.store().Get("k"));
            assertEquals("v", b.store().Get("k"));
            assertEquals("v", c.store().Get("k"));
        }
    }

    @Test
    void deleteRoundReturnsDeleteResponse() throws Exception {
        try (RmiHarness harness = new RmiHarness()) {
            PeerDirectory peers = new PeerDirectory(harness.bind("1", new FakeNode()));
            peers.add(harness.bind("2", new FakeNode()));
            peers.add(harness.bind("3", new FakeNode()));
            PaxosProposer proposer = new PaxosProposer("1", peers, new RmiTransport());

            proposer.propose(new TransactionPacket(put("k", "v"), Vote.YES));
            Response r = proposer.propose(new TransactionPacket(del("k"), Vote.YES));

            assertTrue(r.header.startsWith("DELETE: k"));
        }
    }

    @Test
    void throwsWhenMajorityUnreachable() throws Exception {
        try (RmiHarness harness = new RmiHarness()) {
            PeerDirectory peers = new PeerDirectory(harness.bind("3", new FakeNode()));
            peers.add(new NodeAddress("1", "127.0.0.1", String.valueOf(RmiHarness.freePort())));
            peers.add(new NodeAddress("2", "127.0.0.1", String.valueOf(RmiHarness.freePort())));
            PaxosProposer proposer = new PaxosProposer("3", peers, new RmiTransport());

            assertThrows(RemoteException.class,
                    () -> proposer.propose(new TransactionPacket(put("k", "v"), Vote.YES)));
        }
    }
}