package manuel.rpckvstore.Node.cluster;

import manuel.rpckvstore.Node.MembershipRpc;
import manuel.rpckvstore.Node.Response;
import manuel.rpckvstore.NodeAddress;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Set;
import java.util.function.Supplier;

public class ClusterMembership {

    private final String selfId;
    private final Supplier<String> selfIp;
    private final int selfPort;
    private final String initialNodeIp;
    private final String initialNodePort;
    private final PeerDirectory peers;
    private final Transport transport;

    public ClusterMembership(String selfId,
                             Supplier<String> selfIp,
                             int selfPort,
                             String initialNodeIp,
                             String initialNodePort,
                             PeerDirectory peers,
                             Transport transport) {
        this.selfId = selfId;
        this.selfIp = selfIp;
        this.selfPort = selfPort;
        this.initialNodeIp = initialNodeIp;
        this.initialNodePort = initialNodePort;
        this.peers = peers;
        this.transport = transport;
    }

    public Response join(String id, String ip, String port) {
        peers.add(new NodeAddress(id, ip, port));
        informOfNewNode();
        return new Response("Joined", "ID:"+id +" :" +ip + " port:" +port +" Join ");
    }

    public void connectToInitialNode() throws RemoteException {
        System.out.println("Connecting to initial node at " + initialNodeIp + ":" + initialNodePort);
        Registry registry;
        try {
            registry = LocateRegistry.getRegistry(initialNodeIp, Integer.parseInt(initialNodePort));
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
        MembershipRpc stub;
        try {
            stub = (MembershipRpc) registry.lookup("Node-0");
        } catch (NotBoundException | RemoteException e) {
            throw new RuntimeException(e);
        }
        Response response = stub.join(selfId, selfIp.get(), String.valueOf(selfPort));
        if (response!= null) {
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
                MembershipRpc stub = transport.lookup(node);
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
