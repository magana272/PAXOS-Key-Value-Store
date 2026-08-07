package manuel.rpckvstore.Node;

public final class PaxosConfig {

    public static final long PREPARE_PHASE_TIMEOUT_MS = 300L;
    public static final long ACCEPT_PHASE_TIMEOUT_MS = 100L;
    public static final long COMMIT_PHASE_TIMEOUT_MS = 300L;
    public static final long HEALTH_CHECK_TIMEOUT_MS = 300L;
    public static final int PROPOSER_MAX_ATTEMPTS = 3;

    public static float acceptorFailRate;
    public static float proposerFailRate;

    public PaxosConfig(float acceptorFailRate, float proposerFailRate) {
        PaxosConfig.acceptorFailRate = acceptorFailRate;
        PaxosConfig.proposerFailRate = proposerFailRate;
    }
}
