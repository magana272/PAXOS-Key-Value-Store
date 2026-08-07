package manuel.rpckvstore;

import manuel.rpckvstore.Node.Response;
import manuel.rpckvstore.Packet.Packet;
import manuel.rpckvstore.testutil.FakeNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PutGetDeleteThreadTest {

    @Test
    void roundTripMarksSuccess() {
        FakeNode stub = new FakeNode();
        PutGetDeleteThread task = new PutGetDeleteThread(stub, "prot", "SEQ");

        task.run();

        assertTrue(task.isSuccess());
    }

    @Test
    void readBackMismatchFailsRun() {
        FakeNode stub = new FakeNode() {
            @Override
            public Response Get(Packet p) {
                return new Response("GET", "WRONG");
            }
        };
        PutGetDeleteThread task = new PutGetDeleteThread(stub, "prot", "SEQ");

        task.run();

        assertFalse(task.isSuccess());
    }

    @Test
    void remoteExceptionLeavesFailure() {
        PutGetDeleteThread task =
                new PutGetDeleteThread(new FakeNode().failRemote(true), "prot", "SEQ");

        task.run();

        assertFalse(task.isSuccess());
    }

    @Test
    void defaultsToNotSuccessBeforeRun() {
        assertFalse(new PutGetDeleteThread(new FakeNode(), "p", "s").isSuccess());
    }
}