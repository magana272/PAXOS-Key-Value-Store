package manuel.rpckvstore.Node;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PaxosConfigTest {

    @AfterEach
    void resetStatics() {
        new PaxosConfig(0f, 0f);
    }

    @Test
    void constructorSetsStaticRates() {
        new PaxosConfig(0.25f, 0.75f);

        assertEquals(0.25f, PaxosConfig.acceptorFailRate);
        assertEquals(0.75f, PaxosConfig.proposerFailRate);
    }

    @Test
    void constantsAreExposed() {
        assertEquals(100L, PaxosConfig.ACCEPT_PHASE_TIMEOUT_MS);
        assertEquals(3, PaxosConfig.PROPOSER_MAX_ATTEMPTS);
    }
}