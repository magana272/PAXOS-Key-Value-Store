package manuel.rpckvstore.testutil;

import manuel.rpckvstore.Node.BaseServer;
import manuel.rpckvstore.NodeAddress;

import java.io.IOException;
import java.net.ServerSocket;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;

public class RmiHarness implements AutoCloseable {

    private final Registry registry;
    private final int port;
    private final List<BaseServer> exported = new ArrayList<>();

    public RmiHarness() throws RemoteException {
        this(freePort());
    }

    public RmiHarness(int port) throws RemoteException {
        this.port = port;
        this.registry = LocateRegistry.createRegistry(port);
    }

    public int port() {
        return port;
    }

    public Registry registry() {
        return registry;
    }

    public NodeAddress bind(String id, BaseServer server) throws RemoteException {
        BaseServer stub = (BaseServer) UnicastRemoteObject.exportObject(server, 0);
        registry.rebind("Node-" + id, stub);
        exported.add(server);
        return new NodeAddress(id, "127.0.0.1", String.valueOf(port));
    }

    public void unbind(String id) throws Exception {
        registry.unbind("Node-" + id);
    }

    public static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        for (BaseServer server : exported) {
            try {
                UnicastRemoteObject.unexportObject(server, true);
            } catch (NoSuchObjectException ignore) {
            }
        }
        try {
            UnicastRemoteObject.unexportObject(registry, true);
        } catch (NoSuchObjectException ignore) {
        }
    }
}