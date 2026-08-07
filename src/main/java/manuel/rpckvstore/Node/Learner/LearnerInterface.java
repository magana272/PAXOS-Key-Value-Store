package manuel.rpckvstore.Node.Learner;

import manuel.rpckvstore.Node.Response;
import manuel.rpckvstore.Packet.Packet;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface LearnerInterface extends Remote {
    Response join(String id, String ip, String port) throws RemoteException;

    Response Put(Packet p) throws RemoteException;

    Response Get(Packet p) throws RemoteException;

    Response Delete(Packet p) throws RemoteException;

    void Learn(Packet p) throws RemoteException;

    Response Commit(Packet p) throws RemoteException;
}
