package manuel.rpckvstore.Node;

import manuel.rpckvstore.Node.Learner.KeyValueStore;
import manuel.rpckvstore.Node.Learner.LockState;
import manuel.rpckvstore.NodeAddress;
import manuel.rpckvstore.Packet.*;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Node implements BaseServer, Serializable {


    //  Handle the locks of this machine
    private KeyValueStore kv;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.ReadLock kvReadLock = lock.readLock();
    private final ReentrantReadWriteLock.WriteLock kvWriteLock = lock.writeLock();
    private final String NodeID;
    //  For connections:
    String InitializeNodeIP;
    String InitializeNodePortNumber;


    String ServerAddress;
    int portNumber;
    ExecutorService executor;
    private BaseServer stub;
    private LockState lockState = LockState.UNLOCKED;
    private String SequenceNumber = "0";
    private String PromisedSequenceNumber;
    // All node should be aware of other nodes in the system
    private Set<NodeAddress> nodeAddresses;
    // Leader
    private NodeAddress leader;


    private final float ACCEPTOR_FAILRATE;
    private final float PROPOSER_FAILRATE;
//    private final float LEARNER_FAILRATE;


    public KeyValueStore getKVStore() {
        return kv;
    }
    public String getNodeID() {
        return NodeID;
    }

    public String getInitializeNodeIP() {
        return InitializeNodeIP;
    }

    public String getInitializeNodePortNumber() {
        return InitializeNodePortNumber;
    }

    public String getPromisedSequenceNumber() {
        return PromisedSequenceNumber;
    }

    public Set<NodeAddress> getNodeAddresses() {
        return nodeAddresses;
    }

    public int getPortNumber() {
        return portNumber;
    }
    public KeyValueStore getKv() {
        return kv;
    }

    public ReentrantReadWriteLock getLock() {
        return lock;
    }

    public ReentrantReadWriteLock.ReadLock getKvReadLock() {
        return kvReadLock;
    }

    public ReentrantReadWriteLock.WriteLock getKvWriteLock() {
        return kvWriteLock;
    }

    public String getServerAddress() {
        return ServerAddress;
    }

    public ExecutorService getExecutor() {
        return executor;
    }

    public BaseServer getStub() {
        return stub;
    }

    public LockState getLockState() {
        return lockState;
    }

    public String getSequenceNumber() {
        return SequenceNumber;
    }

    public NodeAddress getLeader() {
        return leader;
    }
    @Override
    public void Learn(Packet packet) throws RemoteException {
        System.out.println("========Learning===============");
        TYPE type = packet.getType();
        Packet packet1 = switch (type) {
            case GET -> commitGet(packet);
            case PUT -> commitPut(packet);
            case DELETE -> deletePacket(packet);
        };
    }



    public Node(String NodeID, String InitializeNodeIP, String InitializeNodePortNumber, int portNumber, float acceptorFailrate, float proposerFailrate) throws RemoteException {
        ACCEPTOR_FAILRATE = acceptorFailrate;
        PROPOSER_FAILRATE = proposerFailrate;
        kv = new KeyValueStore();
        this.InitializeNodeIP = InitializeNodeIP;
        this.InitializeNodePortNumber = InitializeNodePortNumber;
        int cores = Runtime.getRuntime().availableProcessors();
        this.executor = Executors.newFixedThreadPool(cores);
        this.ServerAddress = "127.0.0.1";
        this.portNumber = portNumber;
        this.NodeID = NodeID;
        this.nodeAddresses = Collections.synchronizedSet(new HashSet<>());
        nodeAddresses.add(new NodeAddress(NodeID, InitializeNodeIP, InitializeNodePortNumber));
    }

    public static void main(String[] args) {
        // Check if the required arguments are provided
        if (args.length < 5) {
            System.err.println("Usage: java PaxosNode <nodeID> <myIP> <myPort> <initIP> <initPort> [--init]");
            System.exit(1);
        }

        String myID = args[0];        // Unique ID for this node (e.g., Node0, Node1, etc.)
        String myIP = args[1];        // IP address of this node
        String myPort = args[2];      // Port to listen on for RMI
        String initNode = args[3];    // IP address of the initial node
        String initPort = args[4];    // Port of the initial node's RMI registry
        boolean isInitNode = args.length > 5 && args[5].equals("--init"); // Flag to determine if this is the initial node

        if (isInitNode) {
            System.out.println("This is the initial node: " + myID);
        } else {
            System.out.println("This node will connect to the initial node at " + initNode + ":" + initPort);
        }

        // Initialize Node and its components
        try {
            System.out.println("Node ID: " + myID);
            System.out.println("Node IP: " + myIP);
            System.out.println("Listening on port: " + myPort);
            System.out.println("Connecting to init node at " + initNode + ":" + initPort);

            // Create and start the local RMI registry for this node
            int port = Integer.parseInt(myPort);
//            Registry registry = LocateRegistry.createRegistry(port);
            System.out.println("Node Registry started on port " + myPort);

            // Initialize the Node (Proposer/Acceptor/Learner roles)
            float acceptFail = Float.parseFloat(System.getenv().getOrDefault("ACCEPT_FAIL", "0.1"));
            float proposeFail = Float.parseFloat(System.getenv().getOrDefault("PROPOSE_FAIL", "0.1"));
            BaseServer node = new Node(myID, initNode, initPort, port, acceptFail, proposeFail);
            ((Node) node).ServerAddress = myIP;
            BaseServer stub = (BaseServer) UnicastRemoteObject.exportObject(node, port);
            // Bind the node to the registry
            Registry registry = LocateRegistry.createRegistry(port);
            registry.bind("Node-" + myID, stub);  // Bind this node to the registry
            System.out.println("Node " + myID + " bound in registry at Node-" + myID);

            // If this is the initial node, it starts the network
            if (isInitNode) {
                System.out.println("This is the initial node. Initializing the Paxos network...");
                // Initialize your PaxosNetwork here for the initial node
            } else {
                // Connect to the initial node and get all known nodes
                ((Node) node).connectToInitalNode();  // Ensure this method connects to the initial node properly
            }

            // After joining, the node should have the full list of peers to interact with
            System.out.println("Successfully joined the Paxos network!");
//            synchronized (Node.class) {
//                Node.class.wait();
//            }

        } catch (Exception e) {
            System.err.println("Server exception: " + e);
            e.printStackTrace();
        }
    }

    public Packet informLeaderOfTransaction(TransactionPacket packet, String leaderIP, String leaderport) throws RemoteException {
        if (!this.leader.isAlive()) {
            this.nodeAddresses.remove(leader);
            runLeaderElection();
        }
        BaseServer stub;
        Packet responsePacket = null;
        try {
            System.out.println("Informing Coordinator");
//            stub = this.getStub();
            stub = (BaseServer) LocateRegistry.getRegistry(this.leader.getIp(), Integer.parseInt(this.leader.getPort())).lookup("Node-" + this.leader.getId());
            if (stub == null) {
                System.out.println("Stub was null, couldn't retrieve Coordinator");
                throw new RemoteException("Failed to retrieve the Coordinator stub.");
            }
            responsePacket = stub.hasTransaction(packet);

            return responsePacket;
        } catch (NotBoundException | RemoteException e) {
            System.err.println("Couldn't connect to server");
            e.printStackTrace();
        }

        return responsePacket;

    }

    private static final int HAS_TRANSACTION_MAX_ATTEMPTS = 10;
    private static final long ACCEPT_PHASE_TIMEOUT_MS = 100L;

    @Override
    public Packet hasTransaction(TransactionPacket tranPacket) throws RemoteException {
        tranPacket.getPacket().logRecievedRequest();
        for (int attempt = 0; attempt < HAS_TRANSACTION_MAX_ATTEMPTS; attempt++) {
            Packet committed = runPaxosRound(tranPacket);
            if (committed != null) {
                return committed;
            }
            System.out.println("========Retrying Paxos (attempt " + (attempt + 1) + ")========");
        }
        throw new RemoteException("Paxos failed to reach consensus after "
                + HAS_TRANSACTION_MAX_ATTEMPTS + " attempts");
    }

    private Packet runPaxosRound(TransactionPacket tranPacket) throws RemoteException {
        // Phase 1: collect votes
        System.out.println("========GET THE VOTES: Phase 1: Propose Phase========");
        this.SequenceNumber = String.valueOf(Integer.parseInt(this.SequenceNumber) + 1);
        String curr = this.SequenceNumber + "." + this.NodeID;
        ArrayList<Vote> votes = new ArrayList<>();
        for (NodeAddress p : getParticipants()) {
            System.out.println(p.toString());
            try {
                Registry r = LocateRegistry.getRegistry(p.getIp(), Integer.parseInt(p.getPort()));
                BaseServer stub = (BaseServer) r.lookup("Node-" + p.getId());
                Ack ack = stub.Propose(Float.parseFloat(curr));
                votes.add(ack == Ack.NO ? Vote.NO : Vote.YES);
            } catch (NotBoundException e) {
                throw new RuntimeException(e);
            }
        }

        int majority = nodeAddresses.size() / 2;
        long yesVotes = votes.stream().filter(v -> v == Vote.YES).count();
        if (yesVotes <= majority) {
            return null;
        }

        // Phase 2: accept
        System.out.println("========GET THE : Phase 1: Accept Phase========");
        ExecutorService roundExecutor = Executors.newFixedThreadPool(nodeAddresses.size());
        try {
            List<Future<Packet>> futures = new ArrayList<>();
            for (NodeAddress p : nodeAddresses) {
                futures.add(roundExecutor.submit(() -> {
                    try {
                        Registry r = LocateRegistry.getRegistry(p.getIp(), Integer.parseInt(p.getPort()));
                        BaseServer stub = (BaseServer) r.lookup("Node-" + p.getId());
                        return stub.Accept(Float.parseFloat(curr), tranPacket.getPacket());
                    } catch (Exception e) {
                        return null;
                    }
                }));
            }
            int successCount = 0;
            Packet committedPacket = null;
            for (Future<Packet> future : futures) {
                try {
                    Packet response = future.get(ACCEPT_PHASE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    if (response != null) {
                        successCount++;
                        committedPacket = response;
                    }
                } catch (Exception e) {
                    System.err.println("There was a problem commiting at node");
                    System.err.println("This is likely due to simulated ACCEPTOR FAILURE");
                    System.err.println("Paxos is still alive. Majority node accepted");
                }
            }
            System.out.println("========Number Of Accepted Node========");
            System.out.println(successCount);
            System.out.println("=======================================");
            if (successCount < majority || committedPacket == null) {
                return null;
            }
            return committedPacket;
        } finally {
            roundExecutor.shutdownNow();
        }
    }

    // Prepare phase of the Paxos algorithm
    // This method is called by the proposer to prepare a proposal
    @Override
    public Ack Propose(float id) throws RemoteException {

        /*
         * Prepare Phase.
         *  */
        if (PromisedSequenceNumber == null) {
            System.out.println("PromisedSequenceNumber is null");
            System.out.printf("PromisedSequenceNumber is %s%n", id);
            this.PromisedSequenceNumber = String.valueOf(id);
            return Ack.YES;
        } else if (id < Float.parseFloat(PromisedSequenceNumber)) {
            System.out.println("PromisedSequenceNumber is larger than proposed id");
            System.out.printf("Proposed is %s%n", id);
            return Ack.NO;
        } else {

            this.PromisedSequenceNumber = String.valueOf(id);
            System.out.printf("Promised is %s%n", this.PromisedSequenceNumber);
            return Ack.YES;
        }
    }

    // This method is called by the proposer to accept a proposal
    @Override
    public Packet Accept(float sequenceNumber, Packet packet) throws RemoteException {

        if (sequenceNumber < Float.parseFloat(PromisedSequenceNumber)) {
            packet.setResponse("Ignored");
            return packet;
        } else {
            return failingAccept(sequenceNumber, packet);
        }

    }

    private Packet failingAccept(float sequenceNumber, Packet packet) {
        AcceptTask acceptTask = new AcceptTask(packet, ACCEPTOR_FAILRATE);
        Future<Packet> future = executor.submit(acceptTask);
        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    // Second phase of two-phase commit protocol
    // This method is called by the coordinator to inform the acceptors of a decision
    public Packet Commit(Packet packet) throws RemoteException {
        // The server has already prepared and voted so now we must commit
        System.out.println("========Committing===============");
        System.out.println("Committing");
        TYPE type = packet.getType();
        Packet packet1 = null;
        switch (type) {
            case GET:
                packet1 = commitGet(packet);
                break;
            case PUT:
                packet1 = commitPut(packet);
                break;
            case DELETE:
                packet1 = deletePacket(packet);
                break;
        }

        return packet1;
    }

    // Once the coordinator has received a majority of votes, it can commit the transaction
    private Packet commitPut(Packet packet) {
        return executeKvTask(new PutTask(kv, packet));
    }

    // Once the coordinator has received a majority of votes, it can commit the transaction
    private Packet deletePacket(Packet packet) {
        return executeKvTask(new DeleteTask(kv, packet));
    }

    // Once the coordinator has received a majority of votes, it can commit the transaction
    private Packet commitGet(Packet packet) {
        return executeKvTask(new GetTask(kv, packet));
    }

    private Packet executeKvTask(Callable<Packet> task) {
        Future<Packet> future = executor.submit(task);
        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }


    //  The server recieves a request from the client and logs it
    // The server proposes the request to the coordinator
    @Override
    public Packet Put(Packet p) throws RemoteException {
        // Inform the coordinator I have something I want to commit
        if (leader == null) {
            runLeaderElection();
            System.out.println("LeaderIs");
            System.out.println(leader.toString());
            System.out.println(leader.getPort());
            System.out.println(leader.getIp());

        }
        if (!leader.isAlive()) {
            nodeAddresses.remove(leader);
            informOfNewNode();
            runLeaderElection();

        }

        p.logRecievedRequest();
        TransactionPacket packet = new TransactionPacket(p, Vote.YES);
        return informLeaderOfTransaction(packet, this.leader.getIp(), this.leader.getPort());
    }


    @Override
    public Packet Delete(Packet p) throws RemoteException {
        if (leader == null) {
            runLeaderElection();
        }
        p.logRecievedRequest();
        TransactionPacket packet = new TransactionPacket(p, Vote.YES);
        return informLeaderOfTransaction(packet, this.leader.getIp(), this.leader.getPort());
    }

    @Override
    public Packet Get(Packet p) throws RemoteException {
        if (leader == null) {
            runLeaderElection();
        }

        p.logRecievedRequest();
        TransactionPacket packet = new TransactionPacket(p, Vote.YES);
        // TODO: This should inform the leader
        return informLeaderOfTransaction(packet, this.leader.getIp(), this.leader.getPort());
    }

    @Override
    public String join(String id, String ip, String port) throws RemoteException {
        /*
           This function is called from new node.
           1) The inital node is in charge of informing the tracking the new nodes
           2) The inital node once adding the node informs all other node of the current state

         */

        this.nodeAddresses.add(new NodeAddress(id, ip, port));
        System.out.println("Node In Network");
        for (NodeAddress n : nodeAddresses) {
            System.out.println(n.toString());
        }
        this.informOfNewNode();
        return "Joined";
    }

    private String getMyIP() throws UnknownHostException {
        return InetAddress.getLocalHost().toString();
    }

    private Set<NodeAddress> getParticipants() {
        return this.nodeAddresses;
    }

    public void connectToInitalNode() throws RemoteException {
        System.out.println("Connecting to initial node at " + this.InitializeNodeIP + ":" + this.InitializeNodePortNumber);
        Registry registry = null;
        try {
            registry = LocateRegistry.getRegistry(this.InitializeNodeIP, Integer.parseInt(this.InitializeNodePortNumber));
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
        BaseServer stub = null;
        try {
            stub = (BaseServer) registry.lookup("Node-0");
        } catch (NotBoundException | RemoteException e) {
            throw new RuntimeException(e);
        }
        String response = stub.join(this.NodeID, this.ServerAddress, String.valueOf(this.portNumber));
        System.out.println("Response from initial node: " + response);
        if (response.equals("Joined")) {
            System.out.println("Successfully joined the network.");
        } else {
            System.out.println("Failed to join the network: " + response);
        }
    }

    public void informOfNewNode() {
        for (NodeAddress node : nodeAddresses) {
            System.out.println("Informing");
            System.out.println(node.toString());
            try {
                if (node.getId().equals(this.NodeID)) {
                    continue;
                }
                Registry registry = LocateRegistry.getRegistry(node.getIp(), Integer.parseInt(node.getPort()));
                BaseServer stub = (BaseServer) registry.lookup("Node-" + node.getId());
                stub.inform(nodeAddresses);
            } catch (Exception e) {
                System.err.println("Failed to inform node " + node + ": " + e.getMessage());
            }
        }
    }

    @Override
    public void inform(Set<NodeAddress> nodeAddresses) throws RemoteException {
        this.nodeAddresses = nodeAddresses;
        System.out.println("Informing " + nodeAddresses.size() + " nodes");
        for (NodeAddress node : nodeAddresses) {
            System.out.println(node.toString());
//            System.out.println(nodeAddresses.indexOf(node));
        }
    }

    @Override
    public boolean isAlive() throws RemoteException {
        return true;
    }

    public void runLeaderElection() {
        System.out.println("===============================");
        System.out.println("====Running Leader Election=====");
        System.out.println("===============================");
        int leaderID = -1;
        NodeAddress maxleader = null;
        for (NodeAddress node : nodeAddresses) {
            if (Integer.parseInt(node.getId()) > leaderID) {
                leaderID = Integer.parseInt(node.getId());
                maxleader = node;
            }

        }

        this.leader = maxleader;
    }

    @Override
    public String toString() {
        try {
            return String.format("NodeID:%s; IP: %s; Port: %s; Initial Node: %s; Initial Port: %s", this.NodeID, this.getMyIP(), this.portNumber);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    static class PutTask implements Callable<Packet> {
        private final KeyValueStore kv;
        private final Packet p;

        PutTask(KeyValueStore kv, Packet p) {
            this.kv = kv;
            this.p = p;
        }

        @Override
        public Packet call() {
            kv.put(p.getKey(), p.getValue());
            p.setResponse("KEY Value Successfully Set");
            p.logResponseServer();
            return p;
        }
    }

    static class GetTask implements Callable<Packet> {
        private final KeyValueStore kv;
        private final Packet p;

        GetTask(KeyValueStore kv, Packet p) {
            this.kv = kv;
            this.p = p;
        }

        @Override
        public Packet call() {
            p.setResponse(kv.get(p.getKey()));
            p.logResponseServer();
            return p;
        }
    }

    static class DeleteTask implements Callable<Packet> {
        private final KeyValueStore kv;
        private final Packet p;

        DeleteTask(KeyValueStore kv, Packet p) {
            this.kv = kv;
            this.p = p;
        }

        @Override
        public Packet call() {
            kv.delete(p.getKey());
            p.setResponse("Key-Value Successfully Deleted");
            p.logResponseServer();
            return p;
        }
    }

    class AcceptTask implements Callable<Packet> {
        Packet p;
        float failingRate;

        AcceptTask(Packet p, float failingRate) {
            this.p = p;
            this.failingRate = failingRate;
        }

        @Override
        public Packet call() throws Exception {
            float failingRate = this.failingRate;
            float random = ThreadLocalRandom.current().nextFloat();
            if (random < failingRate) {
                System.out.println("Simulating An Accept Failure");
                Thread.sleep(Long.MAX_VALUE);
                return null;
            } else {
                for (NodeAddress node : nodeAddresses) {
                    try {
                        Registry registry = LocateRegistry.getRegistry(node.getIp(), Integer.parseInt(node.getPort()));
                        BaseServer stub = (BaseServer) registry.lookup("Node-" + node.getId());
                        stub.Learn(p);
                    } catch (RemoteException | NotBoundException e) {
                        System.out.println("Failed to learn node: " + node);
                    }
                }
                return Commit(p);
            }

        }
    }
}
