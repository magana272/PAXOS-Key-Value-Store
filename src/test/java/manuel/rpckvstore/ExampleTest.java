package manuel.rpckvstore;

import manuel.rpckvstore.testutil.FakeNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ExampleTest {

    @Test
    void constructorAndAccessorsWireStub() {
        FakeNode stub = new FakeNode();
        Example ex = new Example(stub);

        assertSame(stub, ex.getStub());

        FakeNode replacement = new FakeNode();
        ex.setStub(replacement);
        assertSame(replacement, ex.getStub());
    }

    @Test
    void runExampleStoresThenDeletesFixture() {
        FakeNode stub = new FakeNode();
        Example ex = new Example(stub);

        ex.runExample();

        assertEquals("KEY does not exist",
                stub.store().Get("P10636"),
                "Tau key must be deleted by the end of runExample");
    }

    @Test
    void runExampleSwallowsRemoteException() {
        Example ex = new Example(new FakeNode().failRemote(true));

        assertDoesNotThrow(ex::runExample);
    }
}