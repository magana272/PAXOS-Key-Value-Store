package manuel.rpckvstore.Node.Learner;

import manuel.rpckvstore.Packet.Packet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PaxosLearnerTest {

    private ExecutorService executor;
    private KeyValueStore kv;
    private PaxosLearner learner;

    @BeforeEach
    void setUp() {
        executor = Executors.newSingleThreadExecutor();
        kv = new KeyValueStore();
        learner = new PaxosLearner(kv, executor);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    private static Packet put(String k, String v) {
        return new Packet("{\"TYPE\":\"PUT\",\"KEY\":\"" + k + "\",\"VALUE\":\"" + v + "\"}");
    }

    private static Packet get(String k) {
        return new Packet("{\"TYPE\":\"GET\",\"KEY\":\"" + k + "\"}");
    }

    private static Packet del(String k) {
        return new Packet("{\"TYPE\":\"DELETE\",\"KEY\":\"" + k + "\"}");
    }

    @Test
    void applyPutWritesStore() {
        learner.apply(put("k", "v"));

        assertEquals("v", kv.get("k"));
    }

    @Test
    void applyGetDoesNotMutateStore() {
        learner.apply(put("k", "v"));

        learner.apply(get("k"));

        assertEquals("v", kv.get("k"));
    }

    @Test
    void applyDeleteRemovesKey() {
        learner.apply(put("k", "v"));

        learner.apply(del("k"));

        assertEquals(KeyValueStore.MISSING_KEY_SENTINEL, kv.get("k"));
    }
}
