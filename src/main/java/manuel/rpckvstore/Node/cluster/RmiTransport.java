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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class RmiTransport implements Transport {

    private static final int PROBE_TIMEOUT_MS = 300;

    private final double messageLossRate;

    // Resolved stubs are reusable and thread-safe, so cache them. Only the first
    // call to a peer pays the liveness probe + registry lookup; later calls reuse
    // the stub (and RMI's own connection pool). On failure the caller invalidates
    // the entry so the next round re-resolves.
    private final ConcurrentHashMap<String, BaseServer> stubCache = new ConcurrentHashMap<>();

    public RmiTransport() {
        this(0.0);
    }

    public RmiTransport(double messageLossRate) {
        this.messageLossRate = messageLossRate;
    }

    @Override
    public BaseServer lookup(NodeAddress peer) throws RemoteException, NotBoundException {
        BaseServer cached = stubCache.get(peer.getId());
        if (cached != null) {
            return cached;
        }
        int port = Integer.parseInt(peer.getPort());
        try (Socket probe = new Socket()) {
            probe.connect(new InetSocketAddress(peer.getIp(), port), PROBE_TIMEOUT_MS);
        } catch (IOException e) {
            throw new RemoteException("peer unreachable within " + PROBE_TIMEOUT_MS + "ms: " + peer, e);
        }
        Registry registry = LocateRegistry.getRegistry(peer.getIp(), port);
        BaseServer stub = (BaseServer) registry.lookup("Node-" + peer.getId());
        stubCache.put(peer.getId(), stub);
        return stub;
    }

    @Override
    public BaseServer lookupWithLoss(NodeAddress peer) throws RemoteException, NotBoundException {
        if (messageLossRate > 0.0 && ThreadLocalRandom.current().nextDouble() < messageLossRate) {
            throw new RemoteException("simulated message loss to " + peer);
        }
        return lookup(peer);
    }

    @Override
    public void invalidate(NodeAddress peer) {
        stubCache.remove(peer.getId());
    }
}
