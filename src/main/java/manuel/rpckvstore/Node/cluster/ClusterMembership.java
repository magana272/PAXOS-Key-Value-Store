package manuel.rpckvstore.Node.cluster;

import manuel.rpckvstore.Node.BaseServer;
import manuel.rpckvstore.NodeAddress;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Set;

public class ClusterMembership {

    private final String selfId;
    private final String selfIp;
    private final int selfPort;
    private final String initialNodeIp;
    private final String initialNodePort;
    private final PeerDirectory peers;
    private final RmiTransport transport;

    public ClusterMembership(String selfId,
                             String selfIp,
                             int selfPort,
                             String initialNodeIp,
                             String initialNodePort,
                             PeerDirectory peers,
                             RmiTransport transport) {
        this.selfId = selfId;
        this.selfIp = selfIp;
        this.selfPort = selfPort;
        this.initialNodeIp = initialNodeIp;
        this.initialNodePort = initialNodePort;
        this.peers = peers;
        this.transport = transport;
    }

    public String join(String id, String ip, String port) {
        peers.add(new NodeAddress(id, ip, port));
        System.out.println("Node In Network");
        for (NodeAddress n : peers.snapshot()) {
            System.out.println(n.toString());
        }
        informOfNewNode();
        return "Joined";
    }

    public void connectToInitialNode() throws RemoteException {
        System.out.println("Connecting to initial node at " + initialNodeIp + ":" + initialNodePort);
        Registry registry;
        try {
            registry = LocateRegistry.getRegistry(initialNodeIp, Integer.parseInt(initialNodePort));
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
        BaseServer stub;
        try {
            stub = (BaseServer) registry.lookup("Node-0");
        } catch (NotBoundException | RemoteException e) {
            throw new RuntimeException(e);
        }
        String response = stub.join(selfId, selfIp, String.valueOf(selfPort));
        System.out.println("Response from initial node: " + response);
        if (response.equals("Joined")) {
            System.out.println("Successfully joined the network.");
        } else {
            System.out.println("Failed to join the network: " + response);
        }
    }

    public void informOfNewNode() {
        Set<NodeAddress> snapshot = peers.snapshot();
        for (NodeAddress node : snapshot) {
            if (node.getId().equals(selfId)) {
                continue;
            }
            System.out.println("Informing");
            System.out.println(node.toString());
            try {
                BaseServer stub = transport.lookup(node);
                stub.inform(snapshot);
            } catch (Exception e) {
                System.err.println("Failed to inform node " + node + ": " + e.getMessage());
            }
        }
    }

    public void acceptUpdatedMembership(Set<NodeAddress> updated) {
        peers.replaceAll(updated);
        System.out.println("Informing " + updated.size() + " nodes");
        for (NodeAddress node : updated) {
            System.out.println(node.toString());
        }
    }
}
