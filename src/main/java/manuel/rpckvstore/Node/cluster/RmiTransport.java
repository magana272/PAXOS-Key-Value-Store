package manuel.rpckvstore.Node.cluster;

import manuel.rpckvstore.Node.BaseServer;
import manuel.rpckvstore.NodeAddress;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class RmiTransport {

    public BaseServer lookup(NodeAddress peer) throws RemoteException, NotBoundException {
        Registry registry = LocateRegistry.getRegistry(peer.getIp(), Integer.parseInt(peer.getPort()));
        return (BaseServer) registry.lookup("Node-" + peer.getId());
    }
}
