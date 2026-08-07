package manuel.rpckvstore;

import manuel.rpckvstore.testutil.FakeNode;
import manuel.rpckvstore.testutil.RmiHarness;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class LoadClientTest {

    @Test
    void mainRunsPutWorkload() throws Exception {
        try (RmiHarness harness = new RmiHarness()) {
            harness.bind("0", new FakeNode());

            assertDoesNotThrow(() -> LoadClient.main(
                    new String[]{"127.0.0.1", String.valueOf(harness.port()), "2", "3"}));
        }
    }

    @Test
    void mainRunsGetWorkload() throws Exception {
        try (RmiHarness harness = new RmiHarness()) {
            harness.bind("0", new FakeNode());

            assertDoesNotThrow(() -> LoadClient.main(
                    new String[]{"127.0.0.1", String.valueOf(harness.port()), "2", "2", "GET"}));
        }
    }

    @Test
    void pctHandlesEmptyAndClampedIndex() throws Exception {
        Method pct = LoadClient.class.getDeclaredMethod("pct", List.class, double.class);
        pct.setAccessible(true);

        assertEquals(0.0, (double) pct.invoke(null, List.of(), 50.0));
        assertEquals(9.0, (double) pct.invoke(null, List.of(9L), 200.0));
        assertEquals(1.0, (double) pct.invoke(null, List.of(1L, 2L, 3L), 0.0));
    }
}