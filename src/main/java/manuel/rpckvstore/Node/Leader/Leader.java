package manuel.rpckvstore.Node.Leader;

import java.io.Serializable;

public class Leader implements Serializable {

    private String NodeID;
    private String LeaderIP;
    private String LeaderPortNumber;

    public Leader(String NodeID, String LeaderIP, String LeaderPortNumber) {
        this.NodeID = NodeID;
        this.LeaderIP = LeaderIP;
        this.LeaderPortNumber = LeaderPortNumber;
    }

    public String getNodeID() {
        return NodeID;
    }

    public void setNodeID(String nodeID) {
        NodeID = nodeID;
    }

    public String getLeaderIP() {
        return LeaderIP;
    }

    public void setLeaderIP(String leaderIP) {
        LeaderIP = leaderIP;
    }

    public String getLeaderPortNumber() {
        return LeaderPortNumber;
    }

    public void setLeaderPortNumber(String leaderPortNumber) {
        LeaderPortNumber = leaderPortNumber;
    }
}
