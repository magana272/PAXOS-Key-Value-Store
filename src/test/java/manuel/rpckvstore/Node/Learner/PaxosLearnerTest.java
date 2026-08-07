package manuel.rpckvstore.Node.Learner;

import manuel.rpckvstore.Packet.Packet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PaxosLearnerTest {

    private KeyValueStore kv;
    private PaxosLearner learner;

    @BeforeEach
    void setUp() {
        kv = new KeyValueStore();
        learner = new PaxosLearner(kv);
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

        assertEquals("v", kv.Get("k"));
    }

    @Test
    void applyGetDoesNotMutateStore() {
        learner.apply(put("k", "v"));

        learner.apply(get("k"));

        assertEquals("v", kv.Get("k"));
    }

    @Test
    void applyDeleteRemovesKey() {
        learner.apply(put("k", "v"));

        learner.apply(del("k"));

        assertEquals(KeyValueStore.MISSING_KEY_SENTINEL, kv.Get("k"));
    }
}
