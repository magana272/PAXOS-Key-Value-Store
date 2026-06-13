package manuel.rpckvstore.Node.Learner;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class KeyValueStoreTest {

    @Test
    public void concurrentPutsResolveToOneWinner() throws Exception {
        KeyValueStore kv = new KeyValueStore();
        int writers = 16;
        ExecutorService pool = Executors.newFixedThreadPool(writers);
        AtomicInteger successes = new AtomicInteger();
        try {
            for (int i = 0; i < writers; i++) {
                final String value = "v" + i;
                pool.submit(() -> {
                    if (kv.put("contended", value)) {
                        successes.incrementAndGet();
                    }
                });
            }
            pool.shutdown();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, successes.get(),
                "exactly one putter must win when many race on the same key");
        String winner = kv.get("contended");
        assertNotNull(winner);
        assertTrue(winner.startsWith("v"), "winner should be one of the proposed values");
    }

    @Test
    public void getOnUnknownKeyReturnsSentinel() {
        KeyValueStore kv = new KeyValueStore();

        assertEquals(KeyValueStore.MISSING_KEY_SENTINEL, kv.get("nope"));
    }
}
