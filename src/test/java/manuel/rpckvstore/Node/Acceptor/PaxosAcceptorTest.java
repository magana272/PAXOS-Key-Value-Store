package manuel.rpckvstore.Node.Acceptor;

import manuel.rpckvstore.Node.Learner.KeyValueStore;
import manuel.rpckvstore.Node.Learner.PaxosLearner;
import manuel.rpckvstore.Node.PaxosConfig;
import manuel.rpckvstore.Node.cluster.PeerDirectory;
import manuel.rpckvstore.Node.cluster.RmiTransport;
import manuel.rpckvstore.NodeAddress;
import manuel.rpckvstore.Packet.Ack;
import manuel.rpckvstore.Packet.Packet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class PaxosAcceptorTest {

    private ExecutorService executor;
    private KeyValueStore kv;
    private PaxosAcceptor acceptor;

    @BeforeEach
    void setUp() {
        executor = Executors.newSingleThreadExecutor();
        kv = new KeyValueStore();
        PeerDirectory peers = new PeerDirectory(new NodeAddress("self", "localhost", "1099"));
        acceptor = new PaxosAcceptor(
                new PaxosConfig(0f, 0f),
                executor,
                peers,
                new RmiTransport(),
                new PaxosLearner(kv, executor));
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    private static Packet put(String k, String v) {
        return new Packet("{\"TYPE\":\"PUT\",\"KEY\":\"" + k + "\",\"VALUE\":\"" + v + "\"}");
    }

    @Test
    void firstProposeSetsPromise() {
        assertNull(acceptor.promisedSequenceNumber());

        assertEquals(Ack.YES, acceptor.propose(1.5f));
        assertEquals(1.5f, acceptor.promisedSequenceNumber());
    }

    @Test
    void lowerProposalAfterPromiseIsRejected() {
        acceptor.propose(5.0f);

        assertEquals(Ack.NO, acceptor.propose(1.0f));
        assertEquals(5.0f, acceptor.promisedSequenceNumber());
    }

    @Test
    void higherProposalUpdatesPromise() {
        acceptor.propose(2.0f);

        acceptor.propose(7.0f);

        assertEquals(7.0f, acceptor.promisedSequenceNumber());
    }

    @Test
    void acceptBelowPromiseIsIgnored() {
        acceptor.propose(5.0f);

        Packet response = acceptor.accept(1.0f, put("k", "v"));

        assertEquals("Ignored", response.getResponse());
        assertEquals(KeyValueStore.MISSING_KEY_SENTINEL, kv.get("k"),
                "Ignored Accept must not mutate the store");
    }
}
