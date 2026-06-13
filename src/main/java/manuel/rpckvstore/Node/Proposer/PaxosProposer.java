package manuel.rpckvstore.Node.Proposer;

import manuel.rpckvstore.Node.BaseServer;
import manuel.rpckvstore.Node.PaxosConfig;
import manuel.rpckvstore.Node.cluster.PeerDirectory;
import manuel.rpckvstore.Node.cluster.RmiTransport;
import manuel.rpckvstore.NodeAddress;
import manuel.rpckvstore.Packet.Ack;
import manuel.rpckvstore.Packet.Packet;
import manuel.rpckvstore.Packet.TransactionPacket;
import manuel.rpckvstore.Packet.Vote;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class PaxosProposer {

    private final String nodeId;
    private final PeerDirectory peers;
    private final RmiTransport transport;
    private final PaxosConfig config;

    private long sequenceNumber = 0;

    public PaxosProposer(String nodeId,
                         PeerDirectory peers,
                         RmiTransport transport,
                         PaxosConfig config) {
        this.nodeId = nodeId;
        this.peers = peers;
        this.transport = transport;
        this.config = config;
    }

    public long sequenceNumber() {
        return sequenceNumber;
    }

    public Packet propose(TransactionPacket tranPacket) throws RemoteException {
        tranPacket.getPacket().logRecievedRequest();
        for (int attempt = 0; attempt < PaxosConfig.PROPOSER_MAX_ATTEMPTS; attempt++) {
            Packet committed = runRound(tranPacket);
            if (committed != null) {
                return committed;
            }
            System.out.println("========Retrying Paxos (attempt " + (attempt + 1) + ")========");
        }
        throw new RemoteException("Paxos failed to reach consensus after "
                + PaxosConfig.PROPOSER_MAX_ATTEMPTS + " attempts");
    }

    private Packet runRound(TransactionPacket tranPacket) {
        sequenceNumber++;
        float currentProposal = Float.parseFloat(sequenceNumber + "." + nodeId);

        List<Vote> votes = collectVotes(currentProposal);
        int participantCount = peers.size();
        int strictMajority = participantCount / 2 + 1;
        long yesVotes = votes.stream().filter(v -> v == Vote.YES).count();
        if (yesVotes < strictMajority) {
            return null;
        }
        return runAcceptPhase(currentProposal, tranPacket.getPacket(), strictMajority);
    }

    private List<Vote> collectVotes(float proposal) {
        List<Vote> votes = new ArrayList<>();
        for (NodeAddress peer : peers.snapshot()) {
            try {
                BaseServer stub = transport.lookup(peer);
                Ack ack = stub.Propose(proposal);
                votes.add(ack == Ack.NO ? Vote.NO : Vote.YES);
            } catch (Exception e) {
                votes.add(Vote.NO);
            }
        }
        return votes;
    }

    private Packet runAcceptPhase(float proposal, Packet payload, int strictMajority) {
        int parallelism = Math.max(1, peers.size());
        ExecutorService roundExecutor = Executors.newFixedThreadPool(parallelism);
        try {
            List<Future<Packet>> futures = new ArrayList<>();
            for (NodeAddress peer : peers.snapshot()) {
                futures.add(roundExecutor.submit(() -> {
                    try {
                        BaseServer stub = transport.lookup(peer);
                        return stub.Accept(proposal, payload);
                    } catch (Exception e) {
                        return null;
                    }
                }));
            }
            int successCount = 0;
            Packet committedPacket = null;
            for (Future<Packet> future : futures) {
                try {
                    Packet response = future.get(PaxosConfig.ACCEPT_PHASE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    if (response != null && !"Ignored".equals(response.getResponse())) {
                        successCount++;
                        committedPacket = response;
                    }
                } catch (Exception e) {
                    System.err.println("Accept phase: peer failed or timed out (Paxos still alive on majority)");
                }
            }
            if (successCount < strictMajority || committedPacket == null) {
                return null;
            }
            return committedPacket;
        } finally {
            roundExecutor.shutdownNow();
        }
    }
}
