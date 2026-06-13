package manuel.rpckvstore.Node;

public final class PaxosConfig {

    public static final long ACCEPT_PHASE_TIMEOUT_MS = 100L;
    public static final int PROPOSER_MAX_ATTEMPTS = 10;

    private final float acceptorFailRate;
    private final float proposerFailRate;

    public PaxosConfig(float acceptorFailRate, float proposerFailRate) {
        this.acceptorFailRate = acceptorFailRate;
        this.proposerFailRate = proposerFailRate;
    }

    public float acceptorFailRate() {
        return acceptorFailRate;
    }

    public float proposerFailRate() {
        return proposerFailRate;
    }
}
