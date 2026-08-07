package manuel.rpckvstore.Node;

public interface BaseServer extends
        AcceptorRpc, LearnerRpc, ClientRpc, ConsensusRpc, MembershipRpc {
}
