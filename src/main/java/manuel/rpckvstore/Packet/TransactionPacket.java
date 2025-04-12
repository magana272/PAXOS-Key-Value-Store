package manuel.rpckvstore.Packet;


import com.sun.jdi.VoidType;
import java.io.Serializable;
import java.net.InetAddress;
import java.rmi.RemoteException;

public class TransactionPacket implements Serializable {
    private Vote vote;
    private Packet packet;

    private InetAddress serverIP;
    public TransactionPacket(Packet packet) throws RemoteException {
        this.packet = packet;
        vote = Vote.YES;
    }
    public TransactionPacket(Packet packet, Vote type) throws RemoteException {
        this.packet = packet;
        vote = Vote.YES;
    }
    public Packet getPacket() {
        return packet;
    }
    public void setPacket(Packet packet) {
        this.packet = packet;
    }
    public Vote getVote() {
        return vote;
    }
    public void setVote(Vote vote) {
        this.vote = vote;
    }
}
