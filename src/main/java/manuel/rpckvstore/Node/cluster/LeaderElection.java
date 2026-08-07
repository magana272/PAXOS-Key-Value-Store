package manuel.rpckvstore.Node.cluster;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import manuel.rpckvstore.NodeAddress;
import manuel.rpckvstore.Node.BaseServer;

public class LeaderElection {

    private final PeerDirectory peers;
    private NodeAddress leaderAddress;
    private final RmiTransport transport  = new RmiTransport();
    private BaseServer leaderServer;

    public LeaderElection(PeerDirectory peers) {
        this.peers = peers;
    }

    public synchronized BaseServer current() {
        if (leaderServer == null){
            this.elect();
        }
        return leaderServer;
    }

    public synchronized BaseServer elect() {
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

        this.leaderAddress = winner;
        if (winner == null) {
            this.leaderServer = null;
            return null;
        }

        try {
            this.leaderServer = transport.lookup(leaderAddress);
            return leaderServer;
        } catch (RemoteException e) {
            e.printStackTrace();
        } catch (NotBoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    public synchronized NodeAddress getLeaderAddress(){
        if (this.leaderAddress ==null){
            this.elect();
        }
        return this.leaderAddress;
    }
    public synchronized void demote() {
        peers.remove(leaderAddress);
        leaderAddress = null;
        leaderServer = null;
        elect();
    
    }
}
