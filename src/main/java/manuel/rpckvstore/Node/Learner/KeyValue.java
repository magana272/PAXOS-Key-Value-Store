package manuel.rpckvstore.Node.Learner;

import manuel.rpckvstore.Packet.Packet;

import java.rmi.Remote;
import java.rmi.RemoteException;


public interface KeyValue extends Remote {

    Packet Commit(Packet Packet) throws RemoteException;

    Packet Put(Packet p) throws RemoteException;

    Packet Get(Packet p) throws RemoteException;

    Packet Delete(Packet p) throws RemoteException;

}
