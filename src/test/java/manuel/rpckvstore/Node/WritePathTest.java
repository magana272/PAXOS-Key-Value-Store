package manuel.rpckvstore.Node;

import manuel.rpckvstore.Node.Learner.KeyValueStore;
import manuel.rpckvstore.Packet.Packet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WritePathTest {

    private Node node;
    private KeyValueStore kv;

    @BeforeEach
    public void freshNode() throws Exception {
        node = new Node("w-1", "localhost", "1099", 1099, 0f, 0f);
        kv = node.getKv();
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

        assertEquals("v1", kv.Get("k1"));
    }

    @Test
    public void learnDeleteRemovesKey() throws Exception {
        node.Learn(putPacket("gone", "soon"));
        assertEquals("soon", kv.Get("gone"));

        node.Learn(deletePacket("gone"));

        assertEquals("KEY does not exist", kv.Get("gone"));
    }

    @Test
    public void acceptThenLearnHonoursCommittedValue() throws Exception {
        assertTrue(node.Propose("paxos", 1.0f).isPromised(),
                "acceptor must promise a fresh ballot");

        Packet accepted = node.Accept(1.0f, putPacket("paxos", "ok"));
        assertFalse("Ignored".equals(accepted.getResponse()),
                "Accept with matching sequence number must not be ignored");
        node.Learn(putPacket("paxos", "ok"));

        assertEquals("ok", kv.Get("paxos"));
    }

    @Test
    public void acceptIgnoresStaleSequenceNumber() throws Exception {
        node.Propose("stale", 5.0f);

        Packet response = node.Accept(1.0f, putPacket("stale", "no"));

        assertEquals("Ignored", response.getResponse());
        assertEquals("KEY does not exist", kv.Get("stale"),
                "Ignored Accept must not mutate the store");
    }

    @Test
    public void learnGetDoesNotMutateStore() throws Exception {
        node.Learn(putPacket("readonly", "value"));

        node.Learn(getPacket("readonly"));

        assertEquals("value", kv.Get("readonly"));
    }

    @Test
    public void duplicatePutOnSecondLearnOverwrites() throws Exception {
        node.Learn(putPacket("once", "first"));

        node.Learn(putPacket("once", "second"));

        assertEquals("second", kv.Get("once"),
                "last-write-wins: a later PUT to an existing key overwrites it");
    }

    @Test
    public void deleteOfMissingKeyIsNoOp() throws Exception {
        node.Learn(deletePacket("never-existed"));

        assertEquals("KEY does not exist", kv.Get("never-existed"));
        assertTrue(kv.Put("never-existed", "now"),
                "store must still accept a put after a no-op delete");
    }

    // The cluster replicates a chosen value via the proposer's stub.Commit(...)
    // and serves reads through Node.Get -- the RMI pair the learn-path tests
    // above skip. These pin that pair: a value committed here must read back
    // verbatim in Response.body (what the harness hashes), not the missing
    // sentinel. Regression guard for Commit/Get reading two different stores.
    @Test
    public void commitThenGetRoundTripsRawValue() throws Exception {
        node.Commit(putPacket("prot", "SEQVENCE"));

        Response got = node.Get(getPacket("prot"));

        assertEquals("SEQVENCE", got.body,
                "Node.Get must return the value Node.Commit wrote, raw in the body");
    }

    @Test
    public void getThroughNodeReturnsSentinelForMissingKey() throws Exception {
        assertEquals("KEY does not exist", node.Get(getPacket("never")).body);
    }

    @Test
    public void commitDeleteThenGetReadsMissing() throws Exception {
        node.Commit(putPacket("temp", "v"));
        assertEquals("v", node.Get(getPacket("temp")).body);

        node.Commit(deletePacket("temp"));

        assertEquals("KEY does not exist", node.Get(getPacket("temp")).body);
    }

    /**
     * Last-write-wins under contention: two threads racing a PUT on the same key
     * both succeed, and a subsequent GET must return exactly one of the two
     * written values -- never a corrupted value, never the missing sentinel. The
     * cluster-scale version of this property lives in validation/linearizability.py.
     */
    @Test
    public void concurrentPutsSameKeySettleToOneWrittenValue() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < 50; i++) {
                String key = "race" + i;
                CyclicBarrier startTogether = new CyclicBarrier(2);
                Future<Boolean> putA = pool.submit(() -> {
                    startTogether.await();
                    return kv.Put(key, "A");
                });
                Future<Boolean> putB = pool.submit(() -> {
                    startTogether.await();
                    return kv.Put(key, "B");
                });

                assertTrue(putA.get(5, TimeUnit.SECONDS), "PUT A must succeed");
                assertTrue(putB.get(5, TimeUnit.SECONDS), "PUT B must succeed");

                String stored = kv.Get(key);
                assertTrue("A".equals(stored) || "B".equals(stored),
                        "GET must return exactly one written value, got: " + stored);
                assertNotEquals(KeyValueStore.MISSING_KEY_SENTINEL, stored,
                        "a committed key must not read back as missing");
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
