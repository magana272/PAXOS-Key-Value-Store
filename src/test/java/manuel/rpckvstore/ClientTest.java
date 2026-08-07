package manuel.rpckvstore;

import manuel.rpckvstore.Node.BaseServer;
import manuel.rpckvstore.testutil.FakeNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.rmi.NotBoundException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ClientTest {

    private static Registry registry;
    private static FakeNode fake;
    private static BaseServer stub;

    @BeforeAll
    static void startRegistry() throws Exception {
        registry = LocateRegistry.createRegistry(1099);
        fake = new FakeNode();
        stub = (BaseServer) UnicastRemoteObject.exportObject(fake, 0);
    }

    @AfterAll
    static void stopRegistry() throws Exception {
        UnicastRemoteObject.unexportObject(fake, true);
        UnicastRemoteObject.unexportObject(registry, true);
    }

    @BeforeEach
    void clearRegistry() throws Exception {
        for (String name : registry.list()) {
            registry.unbind(name);
        }
    }

    @Test
    void constructorExposesPortAndRegistry() throws Exception {
        Client c = new Client("127.0.0.1", 4321);

        assertEquals("4321", c.getPortnumber());
        assertNotNull(c.getRegistry());
    }

    @Test
    void getStubReturnsNodeBoundInRegistry() throws Exception {
        registry.rebind("Node-5", stub);
        Client c = new Client("127.0.0.1", 1099);

        assertNotNull(c.getStub());
    }

    @Test
    void getStubThrowsWhenNoNodeBound() throws Exception {
        registry.rebind("Other", stub);
        Client c = new Client("127.0.0.1", 1099);

        assertThrows(NotBoundException.class, c::getStub);
    }
}
