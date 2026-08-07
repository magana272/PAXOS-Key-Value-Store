package manuel.rpckvstore.Node;

import manuel.rpckvstore.Packet.Packet;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface LearnerRpc extends Remote {

    Response Commit(Packet packet) throws RemoteException;

    void Learn(Packet packet) throws RemoteException;
}
