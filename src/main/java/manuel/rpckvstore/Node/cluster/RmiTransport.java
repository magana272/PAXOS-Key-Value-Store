package manuel.rpckvstore.Node.cluster;

import manuel.rpckvstore.Node.BaseServer;
import manuel.rpckvstore.NodeAddress;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.concurrent.ThreadLocalRandom;

public class RmiTransport {

    private static final int PROBE_TIMEOUT_MS = 1000;

    private final double messageLossRate;

    public RmiTransport() {
        this(0.0);
    }

    public RmiTransport(double messageLossRate) {
        this.messageLossRate = messageLossRate;
    }

    public BaseServer lookup(NodeAddress peer) throws RemoteException, NotBoundException {
        int port = Integer.parseInt(peer.getPort());
        try (Socket probe = new Socket()) {
            probe.connect(new InetSocketAddress(peer.getIp(), port), PROBE_TIMEOUT_MS);
        } catch (IOException e) {
            throw new RemoteException("peer unreachable within " + PROBE_TIMEOUT_MS + "ms: " + peer, e);
        }
        Registry registry = LocateRegistry.getRegistry(peer.getIp(), port);
        return (BaseServer) registry.lookup("Node-" + peer.getId());
    }

    public BaseServer lookupWithLoss(NodeAddress peer) throws RemoteException, NotBoundException {
        if (messageLossRate > 0.0 && ThreadLocalRandom.current().nextDouble() < messageLossRate) {
            throw new RemoteException("simulated message loss to " + peer);
        }
        return lookup(peer);
    }
}
