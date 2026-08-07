package manuel.rpckvstore.Node;

import manuel.rpckvstore.NodeAddress;
import manuel.rpckvstore.Packet.Packet;
import manuel.rpckvstore.Packet.TransactionPacket;
import manuel.rpckvstore.Packet.Vote;
import manuel.rpckvstore.testutil.RmiHarness;
import org.junit.jupiter.api.Test;

import java.rmi.RemoteException;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NodeExtrasTest {

    private Node newNode() throws Exception {
        int port = RmiHarness.freePort();
        return new Node("1", "localhost", String.valueOf(port), port, 0f, 0f);
    }

    @Test
    void toStringContainsNodeId() throws Exception {
        assertTrue(newNode().toString().contains("NodeID:1"));
    }

    @Test
    void isAliveReturnsTrue() throws Exception {
        assertTrue(newNode().isAlive());
    }

    @Test
    void equalsSelfIsTrue() throws Exception {
        Node node = newNode();

        assertTrue(node.equals(node));
    }

    @Test
    void equalsNullIsFalse() throws Exception {
        assertFalse(newNode().equals(null));
    }

    @Test
    void equalsDifferentClassIsFalse() throws Exception {
        assertFalse(newNode().equals("not a node"));
    }

    @Test
    void equalsAnotherNodeCastsToNodeAddress() throws Exception {
        Node a = newNode();
        Node b = newNode();

        assertThrows(ClassCastException.class, () -> a.equals(b));
    }

    @Test
    void joinAddsPeerAndReturnsJoined() throws Exception {
        Node node = newNode();

        Response r = node.join("2", "127.0.0.1", String.valueOf(RmiHarness.freePort()));

        assertEquals("Joined", r.header);
        assertEquals(2, node.getNodeAddresses().size());
    }

    @Test
    void informReplacesMembership() throws Exception {
        Node node = newNode();
        Set<NodeAddress> updated = new HashSet<>();
        updated.add(new NodeAddress("5", "127.0.0.1", "6000"));
        updated.add(new NodeAddress("6", "127.0.0.1", "6001"));

        node.inform(updated);

        assertEquals(2, node.getNodeAddresses().size());
    }

    @Test
    void informOfNewNodeWithOnlySelfIsNoOp() throws Exception {
        assertDoesNotThrow(newNode()::informOfNewNode);
    }

    @Test
    void hasTransactionThrowsWhenSelfUnreachable() throws Exception {
        Node node = newNode();
        Packet put = new Packet("{\"TYPE\":\"PUT\",\"KEY\":\"k\",\"VALUE\":\"v\"}");

        assertThrows(RemoteException.class,
                () -> node.hasTransaction(new TransactionPacket(put, Vote.YES)));
    }

    @Test
    void connectToInitalNodeThrowsWithoutInitialNode() throws Exception {
        int port = RmiHarness.freePort();
        Node node = new Node("1", "127.0.0.1", String.valueOf(RmiHarness.freePort()), port, 0f, 0f);

        assertThrows(RuntimeException.class, node::connectToInitalNode);
    }
}