package manuel.rpckvstore.bdd.steps;

import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import manuel.rpckvstore.Node.Learner.KeyValueStore;
import manuel.rpckvstore.Node.Node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class KVStoreSteps {

    private Node node;
    private KeyValueStore kv;
    private boolean lastPutResult;
    private boolean lastDeleteResult;

    @Before
    public void reset() {
        node = null;
        kv = null;
        lastPutResult = false;
        lastDeleteResult = false;
    }

    @Given("a fresh single-node PAXOS cluster")
    public void freshSingleNodeCluster() throws Exception {
        node = new Node("bdd-1", "localhost", "1099", 1099, 0f, 0f);
        kv = node.getKv();
    }

    @Given("{string} is already set to {string}")
    public void keyAlreadySetTo(String key, String value) {
        assertTrue(kv.put(key, value),
                "precondition put for key '" + key + "' should succeed on a fresh store");
    }

    @When("I put {string} with value {string}")
    public void iPutWithValue(String key, String value) {
        lastPutResult = kv.put(key, value);
    }

    @When("I delete {string}")
    public void iDelete(String key) {
        lastDeleteResult = kv.delete(key);
    }

    @Then("getting {string} returns {string}")
    public void gettingReturns(String key, String expected) {
        assertEquals(expected, kv.get(key));
    }

    @Then("the put result is false")
    public void thePutResultIsFalse() {
        assertFalse(lastPutResult);
    }

    @Then("the delete result is true")
    public void theDeleteResultIsTrue() {
        assertTrue(lastDeleteResult);
    }

    @Then("the delete result is false")
    public void theDeleteResultIsFalse() {
        assertFalse(lastDeleteResult);
    }
}
