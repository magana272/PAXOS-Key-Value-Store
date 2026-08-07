package manuel.rpckvstore.Node;

import manuel.rpckvstore.Packet.TransactionPacket;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ConsensusRpc extends Remote {

    Response hasTransaction(TransactionPacket tranPacket) throws RemoteException;
}
