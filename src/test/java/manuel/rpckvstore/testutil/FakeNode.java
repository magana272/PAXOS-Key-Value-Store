package manuel.rpckvstore.testutil;

import manuel.rpckvstore.Node.BaseServer;
import manuel.rpckvstore.Node.Response;
import manuel.rpckvstore.Node.Acceptor.PaxosAcceptor;
import manuel.rpckvstore.Node.Learner.KeyValueStore;
import manuel.rpckvstore.Node.Learner.PaxosLearner;
import manuel.rpckvstore.NodeAddress;
import manuel.rpckvstore.Packet.Packet;
import manuel.rpckvstore.Packet.Promise;
import manuel.rpckvstore.Packet.TransactionPacket;

import java.rmi.RemoteException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class FakeNode implements BaseServer {

    private final PaxosAcceptor acceptor = new PaxosAcceptor();
    private final PaxosLearner learner = new PaxosLearner(new KeyValueStore());

    private volatile boolean alive = true;
    private volatile boolean failRemote = false;

    public final AtomicInteger proposeCalls = new AtomicInteger();
    public final AtomicInteger acceptCalls = new AtomicInteger();
    public final AtomicInteger commitCalls = new AtomicInteger();
    public final AtomicInteger informCalls = new AtomicInteger();

    public FakeNode alive(boolean value) {
        this.alive = value;
        return this;
    }

    public FakeNode failRemote(boolean value) {
        this.failRemote = value;
        return this;
    }

    public KeyValueStore store() {
        return learner.store();
    }

    @Override
    public Promise Propose(String key, float id) {
        proposeCalls.incrementAndGet();
        return acceptor.propose(key, id);
    }

    @Override
    public Packet Accept(float sequenceNumber, Packet packet) {
        acceptCalls.incrementAndGet();
        return acceptor.accept(sequenceNumber, packet);
    }

    @Override
    public void Learn(Packet packet) {
        learner.apply(packet);
    }

    @Override
    public Response Commit(Packet packet) {
        commitCalls.incrementAndGet();
        learner.apply(packet);
        acceptor.advanceInstance(packet.getKey());
        return null;
    }

    @Override
    public Response Put(Packet p) throws RemoteException {
        if (failRemote) {
            throw new RemoteException("simulated remote failure");
        }
        return learner.apply(p);
    }

    @Override
    public Response Get(Packet p) throws RemoteException {
        if (failRemote) {
            throw new RemoteException("simulated remote failure");
        }
        return learner.apply(p);
    }

    @Override
    public Response Delete(Packet p) throws RemoteException {
        if (failRemote) {
            throw new RemoteException("simulated remote failure");
        }
        return learner.apply(p);
    }

    @Override
    public Response hasTransaction(TransactionPacket tranPacket) {
        return learner.apply(tranPacket.getPacket());
    }

    @Override
    public Response join(String id, String ip, String port) {
        return new Response("Joined", "ID:" + id + " :" + ip + " port:" + port);
    }

    @Override
    public void inform(Set<NodeAddress> nodeAddresses) {
        informCalls.incrementAndGet();
    }

    @Override
    public boolean isAlive() {
        return alive;
    }
}