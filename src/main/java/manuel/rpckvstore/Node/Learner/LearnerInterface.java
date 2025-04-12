package manuel.rpckvstore.Node.Learner;

import manuel.rpckvstore.Packet.Packet;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface LearnerInterface extends Remote, KeyValue {
    String join(String id, String ip, String port) throws RemoteException;

    Packet Put(Packet p) throws RemoteException;

    Packet Get(Packet p) throws RemoteException;

    Packet Delete(Packet p) throws RemoteException;

    void Learn(Packet p) throws RemoteException;
}
