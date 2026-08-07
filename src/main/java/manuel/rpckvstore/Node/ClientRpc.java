package manuel.rpckvstore.Node;

import manuel.rpckvstore.Packet.Packet;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ClientRpc extends Remote {

    Response Put(Packet p) throws RemoteException;

    Response Get(Packet p) throws RemoteException;

    Response Delete(Packet p) throws RemoteException;
}
