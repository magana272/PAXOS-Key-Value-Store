package manuel.rpckvstore.Node.Acceptor;

import manuel.rpckvstore.Node.BaseServer;
import manuel.rpckvstore.Node.Learner.PaxosLearner;
import manuel.rpckvstore.Node.PaxosConfig;
import manuel.rpckvstore.Node.cluster.PeerDirectory;
import manuel.rpckvstore.Node.cluster.RmiTransport;
import manuel.rpckvstore.NodeAddress;
import manuel.rpckvstore.Packet.Ack;
import manuel.rpckvstore.Packet.Packet;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

public class PaxosAcceptor {

    private final PaxosConfig config;
    private final ExecutorService executor;
    private final PeerDirectory peers;
    private final RmiTransport transport;
    private final PaxosLearner learner;

    private Float promisedSequenceNumber;

    public PaxosAcceptor(PaxosConfig config,
                         ExecutorService executor,
                         PeerDirectory peers,
                         RmiTransport transport,
                         PaxosLearner learner) {
        this.config = config;
        this.executor = executor;
        this.peers = peers;
        this.transport = transport;
        this.learner = learner;
    }

    public Float promisedSequenceNumber() {
        return promisedSequenceNumber;
    }

    public Ack propose(float id) {
        if (promisedSequenceNumber == null) {
            promisedSequenceNumber = id;
            return Ack.YES;
        }
        if (id < promisedSequenceNumber) {
            return Ack.NO;
        }
        promisedSequenceNumber = id;
        return Ack.YES;
    }

    public Packet accept(float sequenceNumber, Packet packet) {
        if (promisedSequenceNumber != null && sequenceNumber < promisedSequenceNumber) {
            packet.setResponse("Ignored");
            return packet;
        }
        Future<Packet> future = executor.submit(broadcastThenApply(packet));
        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private Callable<Packet> broadcastThenApply(Packet packet) {
        return () -> {
            if (ThreadLocalRandom.current().nextFloat() < config.acceptorFailRate()) {
                System.out.println("Simulating An Accept Failure");
                Thread.sleep(Long.MAX_VALUE);
                return null;
            }
            for (NodeAddress peer : peers.snapshot()) {
                try {
                    BaseServer stub = transport.lookup(peer);
                    stub.Learn(packet);
                } catch (Exception e) {
                    System.out.println("Failed to learn node: " + peer);
                }
            }
            return learner.apply(packet);
        };
    }
}
