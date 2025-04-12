package manuel.rpckvstore.Node;

import org.junit.jupiter.api.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class NodeTest {

    @Test
    public void testNodeJoinOnConstruction() throws Exception {
        Node node = new Node("1", "localhost", "1099", 1099, .5f, .5f);

        // Check basic fields
        assertEquals("1", node.getNodeID());
        assertEquals("127.0.0.1", node.getServerAddress());
        assertEquals(1099, node.getPortNumber());
        assertEquals(node.getNodeAddresses().size(), 1);
    }


    @Test
    public void propose() {
    }

    @Test
    public void accept() {
    }

    @Test
    public void learn() {
    }

    @Test
    public void commit() {
    }

    @Test
    public void put() {
    }

    @Test
    public void delete() {
    }

    @Test
    public void get() {
    }

    @Test
    public void join() {
    }


    @Test
    public void ack() {
    }

    @Test
    public void NAck() {
    }

    @Test
    public void setStub() {
    }

    @Test
    public void getRegistry() {
    }

    @Test
    public void getStub() {
    }

    @Test
    public void getNodeList() {
    }

    @Test
    public void connectToInitalNode() {
    }

    @Test
    public void informOfNewNode() {
    }

    @Test
    public void inform() {
    }

    @Test
    public void main() {
    }
}