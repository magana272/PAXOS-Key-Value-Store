package manuel.rpckvstore.Node.Acceptor;

import manuel.rpckvstore.Node.Learner.KeyValueStore;
import manuel.rpckvstore.Node.PaxosConfig;
import manuel.rpckvstore.Packet.Packet;
import manuel.rpckvstore.Packet.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PaxosAcceptorTest {

    private KeyValueStore kv;
    private PaxosAcceptor acceptor;

    @BeforeEach
    void setUp() {
        kv = new KeyValueStore();
        acceptor = new PaxosAcceptor();
    }

    @AfterEach
    void resetFailRate() {
        new PaxosConfig(0f, 0f);
    }

    private static Packet put(String k, String v) {
        return new Packet("{\"TYPE\":\"PUT\",\"KEY\":\"" + k + "\",\"VALUE\":\"" + v + "\"}");
    }

    @Test
    void firstProposeSetsPromise() {
        assertNull(acceptor.promisedSequenceNumber("k"));

        assertTrue(acceptor.propose("k", 1.5f).isPromised());
        assertEquals(1.5f, acceptor.promisedSequenceNumber("k"));
    }

    @Test
    void lowerProposalAfterPromiseIsRejected() {
        acceptor.propose("k", 5.0f);

        assertFalse(acceptor.propose("k", 1.0f).isPromised());
        assertEquals(5.0f, acceptor.promisedSequenceNumber("k"));
    }

    @Test
    void higherProposalUpdatesPromise() {
        acceptor.propose("k", 2.0f);

        acceptor.propose("k", 7.0f);

        assertEquals(7.0f, acceptor.promisedSequenceNumber("k"));
    }

    @Test
    void acceptBelowPromiseIsIgnored() {
        acceptor.propose("k", 5.0f);

        Packet response = acceptor.accept(1.0f, put("k", "v"));

        assertEquals("Ignored", response.getResponse());
        assertEquals(KeyValueStore.MISSING_KEY_SENTINEL, kv.Get("k"),
                "Ignored Accept must not mutate the store");
    }

    @Test
    void freshAcceptorPromisesWithNoAcceptedValue() {
        Promise promise = acceptor.propose("k", 1.0f);

        assertTrue(promise.isPromised());
        assertFalse(promise.hasAcceptedValue(),
                "a fresh acceptor has nothing accepted to report");
        assertNull(promise.getAcceptedBallot());
    }

    @Test
    void promiseReportsPreviouslyAcceptedValue() {
        acceptor.propose("k", 1.0f);
        acceptor.accept(1.0f, put("k", "v"));
        Promise promise = acceptor.propose("k", 2.0f);
        assertTrue(promise.isPromised());
        assertTrue(promise.hasAcceptedValue());
        assertEquals(1.0f, promise.getAcceptedBallot().floatValue());
        assertEquals("v", promise.getAcceptedValue().getValue());
    }

    @Test
    void advanceInstanceClearsAcceptedValueSoNextRoundStartsFresh() {
        acceptor.propose("k", 1.0f);
        acceptor.accept(1.0f, put("k", "v"));
        acceptor.advanceInstance("k");
        Promise promise = acceptor.propose("k", 2.0f);

        assertTrue(promise.isPromised());
        assertFalse(promise.hasAcceptedValue(),
                "a committed value must be cleared so the next instance starts fresh");
        assertNull(promise.getAcceptedBallot());
        assertEquals(2.0f, acceptor.promisedSequenceNumber("k"),
                "the promise number stays monotonic across the instance boundary");
    }

    @Test
    void acceptRecordsButDoesNotApplyBeforeCommit() {
        acceptor.propose("k", 1.0f);
        Packet response = acceptor.accept(1.0f, put("k", "v"));

        assertEquals("Accepted", response.getResponse());
        assertEquals(KeyValueStore.MISSING_KEY_SENTINEL, kv.Get("k"),
                "a single acceptor's accept must not commit to the store before a majority");
    }

    @Test
    @Tag("spec")
    void unrelatedKeysDoNotShareConsensusState() {
        acceptor.propose("alpha", 5.0f);

        Promise p = acceptor.propose("beta", 2.0f);

        assertTrue(p.isPromised(),
                "an unrelated key's ballot must not be blocked by another key's promise");
    }

    @Test
    void equalBallotDoesNotRejectAndKeepsPromise() {
        acceptor.propose("k", 5.0f);

        Promise p = acceptor.propose("k", 5.0f);

        assertTrue(p.isPromised(),
                "an equal ballot is not below the promise, so it is not rejected (impl guards id < promised)");
        assertEquals(5.0f, acceptor.promisedSequenceNumber("k"));
    }

    @Test
    void acceptReturnsNullWhenFailRateForcesDrop() {
        new PaxosConfig(1.0f, 0f);
        acceptor.propose("k", 1.0f);

        assertNull(acceptor.accept(1.0f, put("k", "v")),
                "a forced accept failure must drop the packet (return null)");
        assertEquals(KeyValueStore.MISSING_KEY_SENTINEL, kv.Get("k"));
    }

    @Test
    void advanceInstanceOnUnknownKeyIsNoOp() {
        acceptor.advanceInstance("never-seen");

        assertNull(acceptor.promisedSequenceNumber("never-seen"));
    }

    @Test
    void higherPromiseThenStaleAcceptIsIgnoredAndPromiseSurvives() {
        acceptor.propose("k", 5.0f);

        Packet response = acceptor.accept(1.0f, put("k", "stale"));

        assertEquals("Ignored", response.getResponse(),
                "an accept below the promised ballot must be ignored");
        assertEquals(KeyValueStore.MISSING_KEY_SENTINEL, kv.Get("k"),
                "an ignored accept must not mutate the store");
        assertEquals(5.0f, acceptor.promisedSequenceNumber("k"),
                "the higher promise must survive a rejected lower accept");
    }
}
