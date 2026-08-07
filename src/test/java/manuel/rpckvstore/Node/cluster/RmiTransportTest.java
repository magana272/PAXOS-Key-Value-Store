package manuel.rpckvstore.Node.cluster;

import manuel.rpckvstore.Node.BaseServer;
import manuel.rpckvstore.NodeAddress;
import manuel.rpckvstore.testutil.FakeNode;
import manuel.rpckvstore.testutil.RmiHarness;
import org.junit.jupiter.api.Test;

import java.rmi.RemoteException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RmiTransportTest {

    @Test
    void lookupResolvesBoundNode() throws Exception {
        try (RmiHarness harness = new RmiHarness()) {
            NodeAddress addr = harness.bind("3", new FakeNode());
            RmiTransport transport = new RmiTransport();

            BaseServer stub = transport.lookup(addr);

            assertNotNull(stub);
            assertTrue(stub.isAlive());
        }
    }

    @Test
    void lookupThrowsWhenPeerUnreachable() {
        NodeAddress dead = new NodeAddress("1", "127.0.0.1", String.valueOf(RmiHarness.freePort()));
        RmiTransport transport = new RmiTransport();

        assertThrows(RemoteException.class, () -> transport.lookup(dead));
    }

    @Test
    void lookupWithLossAlwaysDropsAtRateOne() {
        NodeAddress addr = new NodeAddress("1", "127.0.0.1", "1099");
        RmiTransport transport = new RmiTransport(1.0);

        assertThrows(RemoteException.class, () -> transport.lookupWithLoss(addr));
    }

    @Test
    void lookupWithLossDelegatesWhenNoLoss() throws Exception {
        try (RmiHarness harness = new RmiHarness()) {
            NodeAddress addr = harness.bind("5", new FakeNode());
            RmiTransport transport = new RmiTransport(0.0);

            assertNotNull(transport.lookupWithLoss(addr));
        }
    }
}