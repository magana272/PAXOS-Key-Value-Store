package manuel.rpckvstore.Node.cluster;

import manuel.rpckvstore.NodeAddress;

public class LeaderElection {

    private final PeerDirectory peers;
    private NodeAddress leader;

    public LeaderElection(PeerDirectory peers) {
        this.peers = peers;
    }

    public NodeAddress current() {
        return leader;
    }

    public NodeAddress elect() {
        System.out.println("===============================");
        System.out.println("====Running Leader Election=====");
        System.out.println("===============================");
        int highestId = -1;
        NodeAddress winner = null;
        for (NodeAddress node : peers.snapshot()) {
            int candidate = Integer.parseInt(node.getId());
            if (candidate > highestId) {
                highestId = candidate;
                winner = node;
            }
        }
        this.leader = winner;
        return leader;
    }

    public void demote(NodeAddress dead) {
        if (dead != null && dead.equals(leader)) {
            leader = null;
        }
    }
}
