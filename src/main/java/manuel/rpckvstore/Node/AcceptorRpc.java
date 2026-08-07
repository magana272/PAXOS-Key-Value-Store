package manuel.rpckvstore.Node;

import manuel.rpckvstore.Packet.Packet;
import manuel.rpckvstore.Packet.Promise;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface AcceptorRpc extends Remote {

    Promise Propose(String key, float id) throws RemoteException;

    Packet Accept(float sequenceNumber, Packet packet) throws RemoteException;
}
