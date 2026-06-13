package manuel.rpckvstore.Packet;

import java.io.Serializable;
import java.rmi.RemoteException;

public class TransactionPacket implements Serializable {
    private Vote vote;
    private Packet packet;

    public TransactionPacket(Packet packet) throws RemoteException {
        this(packet, Vote.YES);
    }

    public TransactionPacket(Packet packet, Vote vote) throws RemoteException {
        this.packet = packet;
        this.vote = vote;
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
