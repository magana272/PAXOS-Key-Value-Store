package manuel.rpckvstore.Node;

import manuel.rpckvstore.Node.Acceptor.PaxosAcceptor;
import manuel.rpckvstore.Node.Learner.KeyValueStore;
import manuel.rpckvstore.Node.Learner.PaxosLearner;
import manuel.rpckvstore.Node.Proposer.PaxosProposer;
import manuel.rpckvstore.Node.cluster.ClusterMembership;
import manuel.rpckvstore.Node.cluster.LeaderElection;
import manuel.rpckvstore.Node.cluster.PeerDirectory;
import manuel.rpckvstore.Node.cluster.RmiTransport;
import manuel.rpckvstore.NodeAddress;
import manuel.rpckvstore.Packet.Ack;
import manuel.rpckvstore.Packet.Packet;
import manuel.rpckvstore.Packet.TransactionPacket;
import manuel.rpckvstore.Packet.Vote;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Node implements BaseServer, Serializable {

    private final String NodeID;
    private final String InitializeNodeIP;
    private final String InitializeNodePortNumber;
    private final int portNumber;
    String ServerAddress;

    private final ExecutorService executor;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private final PeerDirectory peers;
    private final RmiTransport transport;
    private final PaxosLearner learner;
    private final PaxosAcceptor acceptor;
    private final PaxosProposer proposer;
    private final ClusterMembership membership;
    private final LeaderElection election;

    public Node(String NodeID,
                String InitializeNodeIP,
                String InitializeNodePortNumber,
                int portNumber,
                float acceptorFailRate,
                float proposerFailRate) throws RemoteException {
        this.NodeID = NodeID;
        this.InitializeNodeIP = InitializeNodeIP;
        this.InitializeNodePortNumber = InitializeNodePortNumber;
        this.portNumber = portNumber;
        this.ServerAddress = "127.0.0.1";

        int cores = Runtime.getRuntime().availableProcessors();
        this.executor = Executors.newFixedThreadPool(cores);

        NodeAddress self = new NodeAddress(NodeID, InitializeNodeIP, InitializeNodePortNumber);
        this.peers = new PeerDirectory(self);
        this.transport = new RmiTransport();

        PaxosConfig config = new PaxosConfig(acceptorFailRate, proposerFailRate);
        this.learner = new PaxosLearner(new KeyValueStore(), executor);
        this.acceptor = new PaxosAcceptor(config, executor, peers, transport, learner);
        this.proposer = new PaxosProposer(NodeID, peers, transport, config);
        this.membership = new ClusterMembership(NodeID, ServerAddress, portNumber,
                InitializeNodeIP, InitializeNodePortNumber, peers, transport);
        this.election = new LeaderElection(peers);
    }

    // ===== Accessors used by tests / Client / main =====

    public String getNodeID() {
        return NodeID;
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

    public ExecutorService getExecutor() {
        return executor;
    }

    public ReentrantReadWriteLock getLock() {
        return lock;
    }

    public Set<NodeAddress> getNodeAddresses() {
        return peers.view();
    }

    public KeyValueStore getKv() {
        return learner.store();
    }

    public String getPromisedSequenceNumber() {
        Float promised = acceptor.promisedSequenceNumber();
        return promised == null ? null : String.valueOf((float) promised);
    }

    public NodeAddress getLeader() {
        return election.current();
    }

    // ===== BaseServer (RMI surface) =====

    @Override
    public Ack Propose(float id) {
        return acceptor.propose(id);
    }

    @Override
    public Packet Accept(float sequenceNumber, Packet packet) {
        return acceptor.accept(sequenceNumber, packet);
    }

    @Override
    public void Learn(Packet packet) {
        System.out.println("========Learning===============");
        learner.apply(packet);
    }

    @Override
    public Packet Commit(Packet packet) {
        System.out.println("========Committing===============");
        return learner.apply(packet);
    }

    @Override
    public Packet Put(Packet p) throws RemoteException {
        return routeThroughLeader(p);
    }

    @Override
    public Packet Get(Packet p) throws RemoteException {
        return routeThroughLeader(p);
    }

    @Override
    public Packet Delete(Packet p) throws RemoteException {
        return routeThroughLeader(p);
    }

    private Packet routeThroughLeader(Packet p) throws RemoteException {
        NodeAddress leader = election.current();
        if (leader == null || !leader.isAlive()) {
            if (leader != null) {
                peers.remove(leader);
                membership.informOfNewNode();
            }
            leader = election.elect();
        }
        p.logRecievedRequest();
        TransactionPacket packet = new TransactionPacket(p, Vote.YES);
        return informLeaderOfTransaction(packet, leader.getIp(), leader.getPort());
    }

    @Override
    public Packet hasTransaction(TransactionPacket tranPacket) throws RemoteException {
        return proposer.propose(tranPacket);
    }

    @Override
    public Packet informLeaderOfTransaction(TransactionPacket packet, String leaderIP, String leaderPort) throws RemoteException {
        NodeAddress leader = election.current();
        if (leader == null) {
            leader = election.elect();
        }
        if (!leader.isAlive()) {
            peers.remove(leader);
            leader = election.elect();
        }
        System.out.println("Informing Coordinator");
        try {
            BaseServer stub = transport.lookup(leader);
            return stub.hasTransaction(packet);
        } catch (NotBoundException e) {
            throw new RemoteException("Coordinator " + leader + " not bound in its registry", e);
        }
    }

    @Override
    public String join(String id, String ip, String port) {
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

    public void runLeaderElection() {
        election.elect();
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
            Node node = new Node(myID, initNode, initPort, port, acceptFail, proposeFail);
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
