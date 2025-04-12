package manuel.rpckvstore.Node.Acceptor;

import manuel.rpckvstore.Packet.Ack;
import manuel.rpckvstore.Packet.Packet;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface AcceptorInterface extends Remote {
    Ack Propose(float id) throws RemoteException;

    Packet Accept(float sequenceNumber, Packet packet) throws RemoteException;


}
