package manuel.rpckvstore;

import manuel.rpckvstore.testutil.FakeNode;
import manuel.rpckvstore.testutil.RmiHarness;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ValidateTest {

    private String[] phase(RmiHarness harness, String phase) {
        return new String[]{"127.0.0.1", String.valueOf(harness.port()), phase, "0", "1", "0"};
    }

    @Test
    void seedPhaseWritesFixture() throws Exception {
        try (RmiHarness harness = new RmiHarness()) {
            FakeNode node = new FakeNode();
            harness.bind("0", node);

            assertDoesNotThrow(() -> Validate.main(phase(harness, "seed")));

            assertEquals("Alice Johnson", node.store().Get("user1"));
        }
    }

    @Test
    void verifyLoadSweepAndDefaultPhasesRun() throws Exception {
        try (RmiHarness harness = new RmiHarness()) {
            harness.bind("0", new FakeNode());

            assertDoesNotThrow(() -> Validate.main(phase(harness, "verify")));
            assertDoesNotThrow(() -> Validate.main(phase(harness, "sweep")));
            assertDoesNotThrow(() -> Validate.main(phase(harness, "load")));
            assertDoesNotThrow(() -> Validate.main(phase(harness, "unknown-phase")));
        }
    }

    @Test
    void opModeDispatchesEachOperation() throws Exception {
        try (RmiHarness harness = new RmiHarness()) {
            harness.bind("0", new FakeNode());
            String host = "127.0.0.1";
            String port = String.valueOf(harness.port());

            assertDoesNotThrow(() -> Validate.main(
                    new String[]{host, port, "op", "0", "1", "0", "PUT", "k", "v"}));
            assertDoesNotThrow(() -> Validate.main(
                    new String[]{host, port, "op", "0", "1", "0", "GET", "k"}));
            assertDoesNotThrow(() -> Validate.main(
                    new String[]{host, port, "op", "0", "1", "0", "DELETE", "k"}));
            assertDoesNotThrow(() -> Validate.main(
                    new String[]{host, port, "op", "0", "1", "0", "FOO", "k"}));
            assertDoesNotThrow(() -> Validate.main(
                    new String[]{host, port, "op", "0", "1", "0", "PUT", "k"}));
            assertDoesNotThrow(() -> Validate.main(
                    new String[]{host, port, "op", "0", "1", "0"}));
        }
    }

    @Test
    void connectFailureEmitsAndReturns() {
        String[] args = {"127.0.0.1", String.valueOf(RmiHarness.freePort()), "seed", "0", "1", "0"};

        assertDoesNotThrow(() -> Validate.main(args));
    }

    @Test
    void inferNodeIdAndSafeHelpers() throws Exception {
        Method inferNodeId = Validate.class.getDeclaredMethod("inferNodeId", String.class);
        inferNodeId.setAccessible(true);
        Method safe = Validate.class.getDeclaredMethod("safe", String.class);
        safe.setAccessible(true);

        assertEquals("3", inferNodeId.invoke(null, "node3"));
        assertEquals("0", inferNodeId.invoke(null, "abc"));
        assertEquals("", safe.invoke(null, (Object) null));
        assertEquals("x", safe.invoke(null, "x"));
    }
}