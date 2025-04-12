package manuel.rpckvstore.Node.Proposer;

import manuel.rpckvstore.NodeAddress;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Set;

public interface ProposerInterface extends Remote {
    void inform(Set<NodeAddress> values) throws RemoteException;
}
