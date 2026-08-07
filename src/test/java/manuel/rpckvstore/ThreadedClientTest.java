package manuel.rpckvstore;

import manuel.rpckvstore.Node.BaseServer;
import manuel.rpckvstore.testutil.FakeNode;
import manuel.rpckvstore.testutil.RmiHarness;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ThreadedClientTest {

    @Test
    void constructorAndAccessorsWork() throws Exception {
        try (RmiHarness harness = new RmiHarness()) {
            harness.bind("0", new FakeNode());
            ThreadedClient c = new ThreadedClient("127.0.0.1", harness.port());

            assertNotNull(c.getRegistry());
            BaseServer stub = c.getStub();
            assertNotNull(stub);
        }
    }

    @Test
    void setProteinPopulatesFixture() throws Exception {
        try (RmiHarness harness = new RmiHarness()) {
            harness.bind("0", new FakeNode());
            ThreadedClient c = new ThreadedClient("127.0.0.1", harness.port());

            c.setProtein();

            assertEquals(6, c.protiens.size());
        }
    }

    @Test
    void mainRoundTripsEveryProtein() throws Exception {
        try (RmiHarness harness = new RmiHarness()) {
            harness.bind("0", new FakeNode());

            assertDoesNotThrow(() -> ThreadedClient.main(
                    new String[]{"127.0.0.1", String.valueOf(harness.port())}));
        }
    }
}