package manuel.rpckvstore.Node;

import manuel.rpckvstore.Node.Learner.KeyValueStore;
import manuel.rpckvstore.Packet.Ack;
import manuel.rpckvstore.Packet.Packet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the PROPOSE -> ACCEPT -> LEARN write path on a single in-process
 * node with zero simulated failure rates. Multi-node propagation is covered
 * by the testBash/ integration scripts.
 */
public class WritePathTest {

    private Node node;
    private KeyValueStore kv;

    @BeforeEach
    public void freshNode() throws Exception {
        node = new Node("w-1", "localhost", "1099", 1099, 0f, 0f);
        kv = Node.getKv();
    }

    private static Packet putPacket(String key, String value) {
        return new Packet("{\"TYPE\":\"PUT\",\"KEY\":\"" + key + "\",\"VALUE\":\"" + value + "\"}");
    }

    private static Packet getPacket(String key) {
        return new Packet("{\"TYPE\":\"GET\",\"KEY\":\"" + key + "\"}");
    }

    private static Packet deletePacket(String key) {
        return new Packet("{\"TYPE\":\"DELETE\",\"KEY\":\"" + key + "\"}");
    }

    @Test
    public void learnPutWritesKeyValueStore() throws Exception {
        node.Learn(putPacket("k1", "v1"));

        assertEquals("v1", kv.get("k1"));
    }

    @Test
    public void learnDeleteRemovesKey() throws Exception {
        node.Learn(putPacket("gone", "soon"));
        assertEquals("soon", kv.get("gone"));

        node.Learn(deletePacket("gone"));

        assertEquals("KEY does not exist", kv.get("gone"));
    }

    @Test
    public void acceptThenLearnHonoursCommittedValue() throws Exception {
        assertEquals(Ack.YES, node.Propose(1.0f));

        Packet accepted = node.Accept(1.0f, putPacket("paxos", "ok"));
        assertFalse("Ignored".equals(accepted.getResponse()),
                "Accept with matching sequence number must not be ignored");
        node.Learn(putPacket("paxos", "ok"));

        assertEquals("ok", kv.get("paxos"));
    }

    @Test
    public void acceptIgnoresStaleSequenceNumber() throws Exception {
        node.Propose(5.0f);

        Packet response = node.Accept(1.0f, putPacket("stale", "no"));

        assertEquals("Ignored", response.getResponse());
        assertEquals("KEY does not exist", kv.get("stale"),
                "Ignored Accept must not mutate the store");
    }

    @Test
    public void learnGetDoesNotMutateStore() throws Exception {
        node.Learn(putPacket("readonly", "value"));

        node.Learn(getPacket("readonly"));

        assertEquals("value", kv.get("readonly"));
    }

    @Test
    public void duplicatePutOnSecondLearnIsRefused() throws Exception {
        node.Learn(putPacket("once", "first"));

        node.Learn(putPacket("once", "second"));

        assertEquals("first", kv.get("once"),
                "KeyValueStore.put must not overwrite an existing key");
    }

    @Test
    public void deleteOfMissingKeyIsNoOp() throws Exception {
        node.Learn(deletePacket("never-existed"));

        assertEquals("KEY does not exist", kv.get("never-existed"));
        assertTrue(kv.put("never-existed", "now"),
                "store must still accept a put after a no-op delete");
    }
}
