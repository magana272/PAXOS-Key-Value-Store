package manuel.rpckvstore.Node;

import manuel.rpckvstore.NodeAddress;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Set;

public interface MembershipRpc extends Remote {

    Response join(String id, String ip, String port) throws RemoteException;

    void inform(Set<NodeAddress> nodeAddresses) throws RemoteException;

    boolean isAlive() throws RemoteException;
}
