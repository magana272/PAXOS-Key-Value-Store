package manuel.rpckvstore.Node;

import manuel.rpckvstore.Node.Learner.KeyValueStore;
import manuel.rpckvstore.Packet.Ack;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NodeTest {

    private Node newNode() throws Exception {
        return new Node("1", "localhost", "1099", 1099, .5f, .5f);
    }

    @Test
    public void testNodeJoinOnConstruction() throws Exception {
        Node node = newNode();

        assertEquals("1", node.getNodeID());
        assertEquals("127.0.0.1", node.getServerAddress());
        assertEquals(1099, node.getPortNumber());
        assertEquals(1, node.getNodeAddresses().size());
    }

    @Test
    public void constructorWiresInitNodeFields() throws Exception {
        Node node = newNode();

        assertEquals("localhost", node.getInitializeNodeIP());
        assertEquals("1099", node.getInitializeNodePortNumber());
        assertNotNull(node.getExecutor());
        assertNotNull(node.getLock());
    }

    @Test
    public void freshKeyValueStorePut() throws Exception {
        KeyValueStore kv = newNode().getKv();

        assertTrue(kv.put("k1", "v1"));
        assertEquals("v1", kv.get("k1"));
    }

    @Test
    public void putDuplicateKeyReturnsFalse() throws Exception {
        KeyValueStore kv = newNode().getKv();
        kv.put("dup", "first");

        assertFalse(kv.put("dup", "second"),
                "put on existing key should refuse to overwrite");
        assertEquals("first", kv.get("dup"));
    }

    @Test
    public void getMissingKeyReturnsSentinel() throws Exception {
        KeyValueStore kv = newNode().getKv();

        assertEquals("KEY does not exist", kv.get("never-set"));
    }

    @Test
    public void deleteExistingKey() throws Exception {
        KeyValueStore kv = newNode().getKv();
        kv.put("k", "v");

        assertTrue(kv.delete("k"));
        assertEquals("KEY does not exist", kv.get("k"));
    }

    @Test
    public void deleteMissingKeyReturnsFalse() throws Exception {
        KeyValueStore kv = newNode().getKv();

        assertFalse(kv.delete("nope"));
    }

    @Test
    public void getNodeListContainsSelf() throws Exception {
        Node node = newNode();

        assertEquals(1, node.getNodeAddresses().size());
        assertTrue(node.getNodeAddresses().stream()
                .anyMatch(a -> "1".equals(a.getId())));
    }

    @Test
    public void promisedSequenceStartsNull() throws Exception {
        Node node = newNode();

        assertNull(node.getPromisedSequenceNumber());
    }

    @Test
    public void proposeFirstTimeAcceptsAndRecordsPromise() throws Exception {
        Node node = newNode();

        Ack ack = node.Propose(1.5f);

        assertEquals(Ack.YES, ack);
        assertEquals("1.5", node.getPromisedSequenceNumber());
    }

    @Test
    public void proposeLowerThanPromiseIsRejected() throws Exception {
        Node node = newNode();
        node.Propose(5.0f);

        assertEquals(Ack.NO, node.Propose(1.0f));
    }

    @Test
    public void proposeEqualOrHigherUpdatesPromise() throws Exception {
        Node node = newNode();
        node.Propose(2.0f);

        node.Propose(7.0f);

        assertEquals("7.0", node.getPromisedSequenceNumber());
    }

    @Test
    public void twoNodesDoNotShareTheirKeyValueStore() throws Exception {
        Node a = new Node("1", "localhost", "1099", 1099, 0f, 0f);
        Node b = new Node("2", "localhost", "1100", 1100, 0f, 0f);

        a.getKv().put("only-on-a", "1");

        assertEquals("1", a.getKv().get("only-on-a"));
        assertEquals("KEY does not exist", b.getKv().get("only-on-a"),
                "each Node must own its own KeyValueStore instance");
    }

    @Test
    @Disabled("Requires a running multi-node RMI cluster; covered by Cucumber suite.")
    public void accept() {
    }

    @Test
    @Disabled("Requires a running multi-node RMI cluster; covered by Cucumber suite.")
    public void learn() {
    }

    @Test
    @Disabled("Requires a running multi-node RMI cluster; covered by Cucumber suite.")
    public void commit() {
    }

    @Test
    @Disabled("Requires a running multi-node RMI cluster; covered by Cucumber suite.")
    public void join() {
    }

    @Test
    @Disabled("Requires a running multi-node RMI cluster; covered by Cucumber suite.")
    public void connectToInitalNode() {
    }

    @Test
    @Disabled("Requires a running multi-node RMI cluster; covered by Cucumber suite.")
    public void informOfNewNode() {
    }

    @Test
    @Disabled("Requires a running multi-node RMI cluster; covered by Cucumber suite.")
    public void inform() {
    }

    @Test
    @Disabled("main() exits the JVM on bad args; covered by integration scripts in testBash/.")
    public void main() {
    }
}
