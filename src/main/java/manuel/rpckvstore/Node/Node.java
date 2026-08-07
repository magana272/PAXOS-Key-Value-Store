package manuel.rpckvstore.Node;

import manuel.rpckvstore.Node.Acceptor.PaxosAcceptor;
import manuel.rpckvstore.Node.Learner.KeyValueStore;
import manuel.rpckvstore.Node.Learner.PaxosLearner;
import manuel.rpckvstore.Node.Proposer.PaxosProposer;
import manuel.rpckvstore.Node.cluster.ClusterMembership;
import manuel.rpckvstore.Node.cluster.Leader;
import manuel.rpckvstore.Node.cluster.PeerDirectory;
import manuel.rpckvstore.Node.cluster.RmiTransport;
import manuel.rpckvstore.NodeAddress;
import manuel.rpckvstore.Packet.Promise;
import manuel.rpckvstore.Packet.Packet;
import manuel.rpckvstore.Packet.TransactionPacket;
import manuel.rpckvstore.Packet.Vote;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Set;

public class Node implements BaseServer, Serializable {

    private final String NodeID;
    private final String InitializeNodeIP;
    private final String InitializeNodePortNumber;
    private final int portNumber;
    private final String clusterName;
    String ServerAddress;

    private final PeerDirectory peers;
    private final RmiTransport transport;
    private final PaxosLearner learner;
    private final PaxosAcceptor acceptor;
    private final PaxosProposer proposer;
    private final ClusterMembership membership;
    private final Leader leader;
    private final CommitLog commitLog;

    public Node(String NodeID,
                String InitializeNodeIP,
                String InitializeNodePortNumber,
                int portNumber,
                float acceptorFailRate,
                float proposerFailRate) throws RemoteException {
        this(NodeID, InitializeNodeIP, InitializeNodePortNumber, portNumber,
                acceptorFailRate, proposerFailRate, 0.0);
    }

    public Node(String NodeID,
                String InitializeNodeIP,
                String InitializeNodePortNumber,
                int portNumber,
                float acceptorFailRate,
                float proposerFailRate,
                double messageLossRate) throws RemoteException {
        this(NodeID, InitializeNodeIP, InitializeNodePortNumber, portNumber,
                acceptorFailRate, proposerFailRate, messageLossRate, null, envClusterName());
    }

    Node(String NodeID,
         String InitializeNodeIP,
         String InitializeNodePortNumber,
         int portNumber,
         float acceptorFailRate,
         float proposerFailRate,
         Leader leaderOverride) throws RemoteException {
        this(NodeID, InitializeNodeIP, InitializeNodePortNumber, portNumber,
                acceptorFailRate, proposerFailRate, 0.0, leaderOverride, envClusterName());
    }

    public Node(String NodeID,
                String InitializeNodeIP,
                String InitializeNodePortNumber,
                int portNumber,
                float acceptorFailRate,
                float proposerFailRate,
                String clusterName) throws RemoteException {
        this(NodeID, InitializeNodeIP, InitializeNodePortNumber, portNumber,
                acceptorFailRate, proposerFailRate, 0.0, null, clusterName);
    }

    private static String envClusterName() {
        return System.getenv().getOrDefault("CLUSTER_NAME", "default");
    }

    private Node(String NodeID,
                String InitializeNodeIP,
                String InitializeNodePortNumber,
                int portNumber,
                float acceptorFailRate,
                float proposerFailRate,
                double messageLossRate,
                Leader leaderOverride,
                String clusterName) throws RemoteException {
        this.NodeID = NodeID;
        this.InitializeNodeIP = InitializeNodeIP;
        this.InitializeNodePortNumber = InitializeNodePortNumber;
        this.portNumber = portNumber;
        this.clusterName = clusterName;
        this.ServerAddress = "127.0.0.1";

        NodeAddress self = new NodeAddress(NodeID, InitializeNodeIP, InitializeNodePortNumber);
        this.peers = new PeerDirectory(self);
        this.transport = new RmiTransport(messageLossRate);

        this.learner = new PaxosLearner(new KeyValueStore());
        this.acceptor = new PaxosAcceptor();
        this.proposer = new PaxosProposer(NodeID, peers, transport);
        this.membership = new ClusterMembership(NodeID, () -> ServerAddress, portNumber,
                InitializeNodeIP, InitializeNodePortNumber, peers, transport);
        this.leader = (leaderOverride != null) ? leaderOverride
                : new Leader(peers, membership, learner.store(), transport);
        String logDir = System.getenv().getOrDefault("LOG_DIR", "logs/" + this.clusterName + "/" + this.NodeID);
        this.commitLog = new CommitLog(logDir);
    }

    public String getNodeID() {
        return NodeID;
    }

    public String getClusterName() {
        return clusterName;
    }

    public String getServerAddress() {
        return ServerAddress;
    }

    public int getPortNumber() {
        return portNumber;
    }

    public String getInitializeNodeIP() {
        return InitializeNodeIP;
    }

    public String getInitializeNodePortNumber() {
        return InitializeNodePortNumber;
    }

    public Set<NodeAddress> getNodeAddresses() {
        return peers.view();
    }

    public KeyValueStore getKv() {
        return learner.store();
    }

    public String getPromisedSequenceNumber(String key) {
        Float promised = acceptor.promisedSequenceNumber(key);
        return promised == null ? null : String.valueOf((float) promised);
    }

    @Override
    public Promise Propose(String key, float id) throws RemoteException {
        return acceptor.Propose(key, id);
    }

    @Override
    public Packet Accept(float sequenceNumber, Packet packet) throws RemoteException {
        return acceptor.Accept(sequenceNumber, packet);
    }

    @Override
    public void Learn(Packet packet) {
        leader.apply(packet);
    }

    @Override
    public Response Put(Packet p) throws RemoteException {
        return submit(p);
    }

    @Override
    public Response Get(Packet p) throws RemoteException {
        return leader.read(NodeID, p);
    }

    @Override
    public Response Delete(Packet p) throws RemoteException {
        return submit(p);
    }

    private Response submit(Packet p) throws RemoteException {
        return leader.submit(new TransactionPacket(p, Vote.YES));
    }

    @Override
    public Response hasTransaction(TransactionPacket tranPacket) throws RemoteException {
        return proposer.propose(tranPacket);
    }

    @Override
    public Response join(String id, String ip, String port) {
        return membership.join(id, ip, port);
    }

    @Override
    public void inform(Set<NodeAddress> nodeAddresses) {
        membership.acceptUpdatedMembership(nodeAddresses);
    }

    @Override
    public boolean isAlive() {
        return true;
    }

    public void connectToInitalNode() throws RemoteException {
        membership.connectToInitialNode();
    }

    public void informOfNewNode() {
        membership.informOfNewNode();
    }

    @Override
    public String toString() {
        try {
            return String.format("NodeID:%s; IP: %s; Port: %d",
                    NodeID, InetAddress.getLocalHost().toString(), portNumber);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Response Commit(Packet packet) throws RemoteException {
        learner.apply(packet);
        acceptor.advanceInstance(packet.getKey());
        commitLog.append(packet.getrequest().toString());
        return null;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        NodeAddress other = (NodeAddress) obj;
        return getNodeID() == other.getId();
    }

    public static void main(String[] args) {
        if (args.length < 5) {
            System.err.println("Usage: java PaxosNode <nodeID> <myIP> <myPort> <initIP> <initPort> [--init]");
            System.exit(1);
        }

        String myID = args[0];
        String myIP = args[1];
        String myPort = args[2];
        String initNode = args[3];
        String initPort = args[4];
        boolean isInitNode = args.length > 5 && args[5].equals("--init");

        System.out.println(isInitNode
                ? "This is the initial node: " + myID
                : "This node will connect to the initial node at " + initNode + ":" + initPort);

        try {
            int port = Integer.parseInt(myPort);
            System.out.println("Node ID: " + myID + " | IP: " + myIP + " | Port: " + myPort);
            System.out.println("Connecting to init node at " + initNode + ":" + initPort);

            float acceptFail = Float.parseFloat(System.getenv().getOrDefault("ACCEPT_FAIL", "0.1"));
            float proposeFail = Float.parseFloat(System.getenv().getOrDefault("PROPOSE_FAIL", "0.1"));
            double msgLoss = Double.parseDouble(System.getenv().getOrDefault("MSG_LOSS", "0.0"));
            Node node = new Node(myID, initNode, initPort, port, acceptFail, proposeFail, msgLoss);
            node.ServerAddress = myIP;

            BaseServer stub = (BaseServer) UnicastRemoteObject.exportObject(node, port);
            Registry registry = LocateRegistry.createRegistry(port);
            registry.bind("Node-" + myID, stub);
            System.out.println("Node " + myID + " bound in registry at Node-" + myID);

            if (isInitNode) {
                System.out.println("This is the initial node. Initializing the Paxos network...");
            } else {
                node.connectToInitalNode();
            }
            System.out.println("Successfully joined the Paxos network!");
        } catch (Exception e) {
            System.err.println("Server exception: " + e);
            e.printStackTrace();
        }
    }
}
