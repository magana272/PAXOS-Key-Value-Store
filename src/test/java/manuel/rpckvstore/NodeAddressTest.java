package manuel.rpckvstore;

import manuel.rpckvstore.testutil.FakeNode;
import manuel.rpckvstore.testutil.RmiHarness;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NodeAddressTest {

    @Test
    void gettersReflectConstructorArgs() {
        NodeAddress a = new NodeAddress("3", "10.0.0.1", "1234");

        assertEquals("3", a.getId());
        assertEquals("10.0.0.1", a.getIp());
        assertEquals("1234", a.getPort());
    }

    @Test
    void settersMutateFields() {
        NodeAddress a = new NodeAddress("1", "h", "1");

        a.setId("9");
        a.setIp("192.168.0.1");
        a.setPort(4321);

        assertEquals("9", a.getId());
        assertEquals("192.168.0.1", a.getIp());
        assertEquals("4321", a.getPort());
    }

    @Test
    void toStringContainsFields() {
        String s = new NodeAddress("2", "host", "77").toString();

        assertTrue(s.contains("id=2"));
        assertTrue(s.contains("ip=host"));
        assertTrue(s.contains("port=77"));
    }

    @Test
    void compareToOrdersByDescendingId() {
        NodeAddress low = new NodeAddress("1", "h", "1");
        NodeAddress high = new NodeAddress("5", "h", "1");

        assertTrue(low.compareTo(high) > 0);
        assertTrue(high.compareTo(low) < 0);
        assertEquals(0, low.compareTo(new NodeAddress("1", "h", "2")));
    }

    @Test
    void isAliveFalseWhenNothingListening() {
        NodeAddress dead = new NodeAddress("1", "127.0.0.1", String.valueOf(RmiHarness.freePort()));

        assertFalse(dead.isAlive());
    }

    @Test
    void isAliveFalseWhenRegistryPresentButNameUnbound() throws Exception {
        try (RmiHarness harness = new RmiHarness()) {
            NodeAddress unbound = new NodeAddress("42", "127.0.0.1", String.valueOf(harness.port()));

            assertFalse(unbound.isAlive());
        }
    }

    @Test
    void isAliveTrueWhenNodeBoundAndAlive() throws Exception {
        try (RmiHarness harness = new RmiHarness()) {
            NodeAddress addr = harness.bind("7", new FakeNode().alive(true));

            assertTrue(addr.isAlive());
        }
    }

    @Test
    void isAliveFalseWhenStubReportsDead() throws Exception {
        try (RmiHarness harness = new RmiHarness()) {
            NodeAddress addr = harness.bind("8", new FakeNode().alive(false));

            assertFalse(addr.isAlive());
        }
    }
}