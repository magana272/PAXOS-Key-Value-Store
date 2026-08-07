package manuel.rpckvstore.Node.cluster;

import manuel.rpckvstore.Node.BaseServer;
import manuel.rpckvstore.NodeAddress;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;

public interface Transport {

    BaseServer lookup(NodeAddress peer) throws RemoteException, NotBoundException;

    BaseServer lookupWithLoss(NodeAddress peer) throws RemoteException, NotBoundException;

    void invalidate(NodeAddress peer);
}
