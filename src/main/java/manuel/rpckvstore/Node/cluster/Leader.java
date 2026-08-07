package manuel.rpckvstore.Node.cluster;

import manuel.rpckvstore.Node.BaseServer;
import manuel.rpckvstore.Node.PaxosConfig;
import manuel.rpckvstore.Node.Response;
import manuel.rpckvstore.Node.Learner.KeyValueStore;
import manuel.rpckvstore.Node.Learner.PaxosLearner;
import manuel.rpckvstore.NodeAddress;
import manuel.rpckvstore.Packet.Packet;
import manuel.rpckvstore.Packet.TransactionPacket;

import java.rmi.RemoteException;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


public class Leader extends PaxosLearner{

    private final LeaderElection election;
    private final ClusterMembership membership;
    // Liveness probes run off the forward path so a wedged leader times out
    // instead of blocking the caller.
    private final ExecutorService healthExecutor = Executors.newCachedThreadPool();
    // When set, submit() forwards straight to this leader with no election.
    // Only used by the leader-forward unit test; null on every production path.
    private final BaseServer pinned;
    // Cluster view, used by read() to decide "am I the leader / am I standalone"
    // without forcing an election on the single-node path. Null on the pinned ctor.
    private final PeerDirectory peers;

    public Leader(PeerDirectory peers, ClusterMembership membership){
        this(peers, membership, new KeyValueStore());
    }

    public Leader(PeerDirectory peers, ClusterMembership membership, KeyValueStore kv){
        this(peers, membership, kv, new RmiTransport());
    }

    public Leader(PeerDirectory peers, ClusterMembership membership, KeyValueStore kv, Transport transport){
        super(kv);
        this.election = new LeaderElection(peers, transport);
        this.membership = membership;
        this.pinned = null;
        this.peers = peers;
    }

    private Leader(BaseServer pinned) {
        super(new KeyValueStore());
        this.election = null;
        this.membership = null;
        this.pinned = pinned;
        this.peers = null;
    }

    public static Leader pinned(BaseServer leader) {
        return new Leader(leader);
    }

    /** Forward a client transaction to the current leader, re-electing if it is gone. */
    public Response submit(TransactionPacket packet) throws RemoteException {
        if (pinned != null) {
            return pinned.hasTransaction(packet);
        }
        return healthyLeader().hasTransaction(packet);
    }

    public Response read(String selfId, Packet packet) throws RemoteException {
        if (pinned != null) {
            return pinned.Get(packet);
        }
        BaseServer remoteLeader = leaderIfRemote(selfId);
        if (remoteLeader == null) {
            return apply(packet);
        }
        return remoteLeader.Get(packet);
    }
    private BaseServer leaderIfRemote(String selfId) throws RemoteException {
        if (peers.size() <= 1) {
            return null;
        }
        NodeAddress leaderAddr = election.getLeaderAddress();
        if (leaderAddr == null || selfId.equals(leaderAddr.getId())) {
            return null;
        }
        return healthyLeader();
    }

    public BaseServer current() {
        return election.current();
    }

    public NodeAddress address() {
        return election.getLeaderAddress();
    }
   

    public void runElection() {
        election.elect();
    }
    
    private BaseServer healthyLeader() throws RemoteException {
        BaseServer leader = election.current();
        if (leader == null) {
            leader = election.elect();
        }
        if (leader == null) {
            throw new RemoteException("no leader could be elected");
        }
        if (!isAlive(leader)) {
            election.demote();
            membership.informOfNewNode();
            leader = election.elect();
            if (leader == null) {
                throw new RemoteException("no leader after failover");
            }
        }
        return leader;
    }

    private boolean isAlive(BaseServer leader) {
        Future<Boolean> probe = healthExecutor.submit(() -> {
            try {
                return leader.isAlive();
            } catch (RemoteException e) {
                return false;
            }
        });
        try {
            return probe.get(PaxosConfig.HEALTH_CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            probe.cancel(true);
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public void inform(Set<NodeAddress> values) throws RemoteException {
        membership.acceptUpdatedMembership(values);
    }
 
}
