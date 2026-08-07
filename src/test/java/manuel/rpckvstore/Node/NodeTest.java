package manuel.rpckvstore.Node;

import manuel.rpckvstore.Node.Learner.KeyValueStore;
import manuel.rpckvstore.Node.cluster.Leader;
import manuel.rpckvstore.NodeAddress;
import manuel.rpckvstore.Packet.Packet;
import manuel.rpckvstore.Packet.Promise;
import manuel.rpckvstore.Packet.TYPE;
import manuel.rpckvstore.Packet.TransactionPacket;
import manuel.rpckvstore.Packet.Vote;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
    }

    @Test
    public void freshKeyValueStorePut() throws Exception {
        KeyValueStore kv = newNode().getKv();

        assertTrue(kv.Put("k1", "v1"));
        assertEquals("v1", kv.Get("k1"));
    }

    @Test
    public void PutDuplicateKeyReturnsFalse() throws Exception {
        KeyValueStore kv = newNode().getKv();
        kv.Put("dup", "first");

        assertFalse(kv.Put("dup", "second"),
                "Put on existing key should refuse to overwrite");
        assertEquals("first", kv.Get("dup"));
    }

    @Test
    public void GetMissingKeyReturnsSentinel() throws Exception {
        KeyValueStore kv = newNode().getKv();

        assertEquals("KEY does not exist", kv.Get("never-set"));
    }

    @Test
    public void deleteExistingKey() throws Exception {
        KeyValueStore kv = newNode().getKv();
        kv.Put("k", "v");

        assertTrue(kv.Delete("k") != null);
        assertEquals("KEY does not exist", kv.Get("k"));
    }

    @Test
    public void deleteMissingKeyReturnsFalse() throws Exception {
        KeyValueStore kv = newNode().getKv();

        assertFalse(kv.Delete("nope") != null);
    }

    @Test
    public void GetNodeListContainsSelf() throws Exception {
        Node node = newNode();

        assertEquals(1, node.getNodeAddresses().size());
        assertTrue(node.getNodeAddresses().stream()
                .anyMatch(a -> "1".equals(a.getId())));
    }

    @Test
    public void promisedSequenceStartsNull() throws Exception {
        Node node = newNode();

        assertNull(node.getPromisedSequenceNumber("k"));
    }

    @Test
    public void proposeFirstTimeAcceptsAndRecordsPromise() throws Exception {
        Node node = newNode();

        Promise promise = node.Propose("k", 1.5f);

        assertTrue(promise.isPromised());
        assertEquals("1.5", node.getPromisedSequenceNumber("k"));
    }

    @Test
    public void proposeLowerThanPromiseIsRejected() throws Exception {
        Node node = newNode();
        node.Propose("k", 5.0f);

        assertFalse(node.Propose("k", 1.0f).isPromised());
    }

    @Test
    public void proposeEqualOrHigherUpdatesPromise() throws Exception {
        Node node = newNode();
        node.Propose("k", 2.0f);

        node.Propose("k", 7.0f);

        assertEquals("7.0", node.getPromisedSequenceNumber("k"));
    }

    @Test
    public void twoNodesDoNotShareTheirKeyValueStore() throws Exception {
        Node a = new Node("1", "localhost", "1099", 1099, 0f, 0f);
        Node b = new Node("2", "localhost", "1100", 1100, 0f, 0f);

        a.getKv().Put("only-on-a", "1");

        assertEquals("1", a.getKv().Get("only-on-a"));
        assertEquals("KEY does not exist", b.getKv().Get("only-on-a"),
                "each Node must own its own KeyValueStore instance");
    }

    private static Packet putPacket(String key, String value) {
        return new Packet("{\"TYPE\":\"PUT\",\"KEY\":\"" + key + "\",\"VALUE\":\"" + value + "\"}");
    }

    private static final class RecordingLeader implements BaseServer {
        private final Response result;
        private TransactionPacket forwarded;
        private int calls;

        RecordingLeader(Response result) {
            this.result = result;
        }

        @Override
        public Response hasTransaction(TransactionPacket tranPacket) {
            this.forwarded = tranPacket;
            this.calls++;
            return result;
        }

        @Override public Promise Propose(String key, float id) { return null; }
        @Override public Packet Accept(float sequenceNumber, Packet packet) { return null; }
        @Override public void Learn(Packet p) { }
        @Override public Response Commit(Packet p) { return null; }
        @Override public Response Put(Packet p) { return null; }
        @Override public Response Get(Packet p) { return null; }
        @Override public Response Delete(Packet p) { return null; }
        @Override public Response join(String id, String ip, String port) { return null; }
        @Override public void inform(Set<NodeAddress> nodeAddresses) { }
        @Override public boolean isAlive() { return true; }
    }

    @Test
    public void nonLeaderForwardsPutToLeaderAndReturnsItsResult() throws Exception {
        Response leaderResult = new Response("Key:k", "v");
        RecordingLeader leaderStub = new RecordingLeader(leaderResult);

        Node n = new Node("1", "localhost", "1099", 1099, 0f, 0f, Leader.pinned(leaderStub));

        Response returned = n.Put(putPacket("k", "v"));

        assertSame(leaderResult, returned,
                "the node must return the leader's Response unchanged");

        assertEquals(1, leaderStub.calls, "the transaction must be forwarded exactly once");
        TransactionPacket forwarded = leaderStub.forwarded;
        assertEquals("k", forwarded.getPacket().getKey());
        assertEquals("v", forwarded.getPacket().getValue());
        assertEquals(TYPE.PUT, forwarded.getPacket().getType());
        assertEquals(Vote.YES, forwarded.getVote());
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
