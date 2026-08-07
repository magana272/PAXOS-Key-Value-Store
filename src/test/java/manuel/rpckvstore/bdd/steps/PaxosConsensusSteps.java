package manuel.rpckvstore.bdd.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import manuel.rpckvstore.Node.Acceptor.PaxosAcceptor;
import manuel.rpckvstore.Node.Learner.KeyValueStore;
import manuel.rpckvstore.Node.Proposer.PaxosProposer;
import manuel.rpckvstore.Packet.Packet;
import manuel.rpckvstore.Packet.Promise;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PaxosConsensusSteps {

    private List<PaxosAcceptor> acceptors;
    private KeyValueStore kv;
    private int majority;

    private Packet lastChosen;      // value the last round actually settled on
    private int lastAcceptedCount;  // acceptors that answered "Accepted" last round

    private static Packet put(String key, String value) {
        return new Packet("{\"TYPE\":\"PUT\",\"KEY\":\"" + key + "\",\"VALUE\":\"" + value + "\"}");
    }

    @Given("a PAXOS cluster of {int} acceptors")
    public void aClusterOfAcceptors(int count) {
        acceptors = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            acceptors.add(new PaxosAcceptor());
        }
        kv = new KeyValueStore();
        majority = count / 2 + 1;
        lastChosen = null;
        lastAcceptedCount = 0;
    }

    private List<Promise> prepare(String key, float ballot) {
        List<Promise> promises = new ArrayList<>();
        for (PaxosAcceptor a : acceptors) {
            promises.add(a.propose(key, ballot));
        }
        return promises;
    }

    @When("client {int} prepares {string} at ballot {int}")
    public void clientPrepares(int client, String key, int ballot) {
        prepare(key, ballot);
    }

    @When("client {int} runs a Paxos round for {string} = {string} at ballot {int}")
    public void clientRunsARound(int client, String key, String value, int ballot) {
        List<Promise> promises = prepare(key, ballot);
        long promised = promises.stream().filter(Promise::isPromised).count();
        if (promised < majority) {
            lastChosen = null;
            lastAcceptedCount = 0;
            return;
        }
        Packet chosen = PaxosProposer.chooseAcceptedValue(promises, put(key, value));
        int accepted = 0;
        for (PaxosAcceptor a : acceptors) {
            Packet response = a.accept(ballot, chosen);
            if ("Accepted".equals(response.getResponse())) {
                accepted++;
            }
        }
        lastChosen = chosen;
        lastAcceptedCount = accepted;
    }

    @Then("a majority of acceptors accepted {string} for {string}")
    public void aMajorityAccepted(String value, String key) {
        assertTrue(lastAcceptedCount >= majority,
                "expected a majority accept, got " + lastAcceptedCount + " of " + acceptors.size());
        assertEquals(key, lastChosen.getKey());
        assertEquals(value, lastChosen.getValue(),
                "the value a majority accepted must be the one Paxos chose");
    }

    @Then("no majority accepted for {string}")
    public void noMajorityAccepted(String key) {
        assertTrue(lastAcceptedCount < majority,
                "expected no majority accept, but " + lastAcceptedCount + " acceptors accepted");
    }

    @Then("Paxos makes client {int} choose {string} for {string}")
    public void paxosMakesClientChoose(int client, String value, String key) {
        assertEquals(value, lastChosen.getValue(),
                "the competing client must adopt the already-accepted value, dropping its own");
    }

    @When("the chosen value for {string} is committed")
    public void theChosenValueIsCommitted(String key) {
        kv.Put(lastChosen.getKey(), lastChosen.getValue());
        for (PaxosAcceptor a : acceptors) {
            a.advanceInstance(key);
        }
    }

    @Then("reading {string} returns {string}")
    public void readingReturns(String key, String expected) {
        assertEquals(expected, kv.Get(key));
    }
}
