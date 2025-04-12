package manuel.rpckvstore.Node;

import manuel.rpckvstore.Node.Acceptor.AcceptorInterface;
import manuel.rpckvstore.Node.Learner.LearnerInterface;
import manuel.rpckvstore.Node.Proposer.ProposerInterface;
import manuel.rpckvstore.NodeAddress;
import manuel.rpckvstore.Packet.Packet;
import manuel.rpckvstore.Packet.TransactionPacket;

import java.rmi.RemoteException;
import java.util.Set;

public interface BaseServer extends LearnerInterface, AcceptorInterface, ProposerInterface {

    Packet informLeaderOfTransaction(TransactionPacket packet, String leaderIP, String leaderport) throws RemoteException;

    Packet hasTransaction(TransactionPacket tranPacket) throws RemoteException;

    void inform(Set<NodeAddress> nodeAddresses) throws RemoteException;

    boolean isAlive() throws RemoteException;

}
