package manuel.rpckvstore.Node.Proposer;

import manuel.rpckvstore.Node.Acceptor.PaxosAcceptor;
import manuel.rpckvstore.Node.Learner.KeyValueStore;
import manuel.rpckvstore.Packet.Packet;
import manuel.rpckvstore.Packet.Promise;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2 value-selection rule: once a proposer has a majority of promises, it must
 * adopt the value tied to the highest accepted ballot (if any acceptor has already
 * accepted one) rather than pushing its own. This is the Paxos safety guarantee that
 * stops an already-(maybe-)chosen value from being overwritten by a later round.
 */
class PaxosProposerTest {

    private static Packet put(String key, String value) {
        return new Packet("{\"TYPE\":\"PUT\",\"KEY\":\"" + key + "\",\"VALUE\":\"" + value + "\"}");
    }

    @Test
    void adoptsValueTiedToHighestAcceptedBallot() {
        Packet own = put("k", "own");
        List<Promise> promises = List.of(
                Promise.of(1.0f, put("k", "early")),
                Promise.of(3.0f, put("k", "latest")),   // highest accepted ballot
                Promise.of(2.0f, put("k", "middle")),
                Promise.rejected());

        Packet chosen = PaxosProposer.chooseAcceptedValue(promises, own);

        assertEquals("latest", chosen.getValue(),
                "must re-propose the value accepted at the highest ballot");
    }

    @Test
    void usesOwnValueWhenNoAcceptorHasAccepted() {
        Packet own = put("k", "own");
        List<Promise> promises = List.of(
                Promise.of(null, null),   // promised, but nothing accepted yet
                Promise.rejected());

        Packet chosen = PaxosProposer.chooseAcceptedValue(promises, own);

        assertEquals("own", chosen.getValue(),
                "with no prior accepted value the proposer may use its own");
    }

    @Test
    void ignoresValuesFromNonPromisingAcceptors() {
        // Promise.rejected() carries no accepted value, so a rejecting acceptor can
        // never contribute a value to selection -- only promising acceptors do.
        Packet own = put("k", "own");
        List<Promise> promises = List.of(
                Promise.of(1.0f, put("k", "kept")),
                Promise.rejected());

        assertEquals("kept", PaxosProposer.chooseAcceptedValue(promises, own).getValue());
    }

    /**
     * The Propose-before-Commit race: a live acceptor Accepts value A, then a higher
     * Propose arrives before Commit/advanceInstance clears the slot. The acceptor must
     * report A in its Promise, the next proposer (carrying its own value B) must adopt A,
     * and applying the chosen value stores A -- the earlier write is never lost.
     */
    @Test
    @Tag("spec")
    void proposeBeforeCommitCarriesAcceptedValueForward() {
        PaxosAcceptor acceptor = new PaxosAcceptor();

        // Value A is accepted but Commit has not run yet (no advanceInstance).
        acceptor.propose("k", 1.0f);
        acceptor.accept(1.0f, put("k", "A"));

        // A higher Propose arrives before Commit -- it must report A.
        Promise p = acceptor.propose("k", 2.0f);
        assertTrue(p.isPromised());
        assertTrue(p.hasAcceptedValue(),
                "a Propose before Commit must report the in-flight accepted value");
        assertEquals("A", p.getAcceptedValue().getValue());

        // The next proposer carries its own value B but must adopt A.
        Packet chosen = PaxosProposer.chooseAcceptedValue(List.of(p), put("k", "B"));
        assertEquals("A", chosen.getValue(),
                "the competing proposer must re-propose the accepted value, dropping its own");

        // Applying the chosen value commits A -- the write is not lost.
        KeyValueStore kv = new KeyValueStore();
        kv.Put(chosen.getKey(), chosen.getValue());
        assertEquals("A", kv.Get("k"));
    }
}
