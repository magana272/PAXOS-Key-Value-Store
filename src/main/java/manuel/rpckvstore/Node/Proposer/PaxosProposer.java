package manuel.rpckvstore.Node.Proposer;

import manuel.rpckvstore.Node.BaseServer;
import manuel.rpckvstore.Node.PaxosConfig;
import manuel.rpckvstore.Node.Response;
import manuel.rpckvstore.Node.cluster.PeerDirectory;
import manuel.rpckvstore.Node.cluster.RmiTransport;
import manuel.rpckvstore.NodeAddress;
import manuel.rpckvstore.Logger.Logger;
import manuel.rpckvstore.Packet.Packet;
import manuel.rpckvstore.Packet.Promise;
import manuel.rpckvstore.Packet.TYPE;
import manuel.rpckvstore.Packet.TransactionPacket;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class PaxosProposer {

    private final String nodeId;
    private final PeerDirectory peers;
    private final RmiTransport transport;

    
    private final ExecutorService roundExecutor = Executors.newCachedThreadPool();
    private final ConcurrentHashMap<String, Long> sequenceByKey = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> keyLocks = new ConcurrentHashMap<>();

    public PaxosProposer(String nodeId,
                         PeerDirectory peers,
                         RmiTransport transport) {
        this.nodeId = nodeId;
        this.peers = peers;
        this.transport = transport;
    }

    private Object lockFor(String key) {
        return keyLocks.computeIfAbsent(key, k -> new Object());
    }

    public Response propose(TransactionPacket tranPacket) throws RemoteException {
        Packet packet = tranPacket.getPacket();
        String key = packet.getKey();
        Logger.log(packet);
        synchronized (lockFor(key)) {
            for (int attempt = 0; attempt < PaxosConfig.PROPOSER_MAX_ATTEMPTS; attempt++) {
                Response committed = runRound(key, tranPacket);
                if (committed != null) {
                    return committed;
                }
                System.out.println("========Retrying Paxos (attempt " + (attempt + 1) + ")========");
            }
        }
        throw new RemoteException("Paxos failed to reach consensus after "
                + PaxosConfig.PROPOSER_MAX_ATTEMPTS + " attempts");
    }

    private Response runRound(String key, TransactionPacket tranPacket) {
        long sequenceNumber = sequenceByKey.merge(key, 1L, Long::sum);
        float currentProposal = Float.parseFloat(sequenceNumber + "." + nodeId);
        List<Promise> promises = this.collectPromises(key, currentProposal);
        int participantCount = peers.size();
        int strictMajority = participantCount / 2 + 1;
        long promised = promises.stream().filter(Promise::isPromised).count();
        if (promised < strictMajority) {
            return null;
        }
        Packet valueToPropose = chooseAcceptedValue(promises, tranPacket.getPacket());
        return runAcceptPhase(currentProposal, valueToPropose, strictMajority);
    }

    private List<Promise> collectPromises(String key, float proposal) {
        List<Promise> promises = new ArrayList<>();
        for (NodeAddress peer : this.peers.snapshot()) {
            try {
                BaseServer stub = this.transport.lookupWithLoss(peer);
                promises.add(stub.Propose(key, proposal));
            } catch (Exception e) {
                promises.add(Promise.rejected());
            }
        }
        return promises;
    }

    public static Packet chooseAcceptedValue(List<Promise> promises, Packet own) {
        Float highestBallot = null;
        Packet chosen = own;
        for (Promise p : promises) {
            if (p.isPromised() && p.hasAcceptedValue()
                    && (highestBallot == null || p.getAcceptedBallot() > highestBallot)) {
                highestBallot = p.getAcceptedBallot();
                chosen = p.getAcceptedValue();
            }
        }
        return chosen;
    }

    private Response runAcceptPhase(float proposal, Packet payload, int strictMajority) {
        List<Callable<Packet>> tasks = new ArrayList<>();
        for (NodeAddress peer : this.peers.snapshot()) {
            tasks.add(() -> {
                try {
                    BaseServer stub = transport.lookupWithLoss(peer);
                    return stub.Accept(proposal, payload);
                } catch (Exception e) {
                    return null;
                }
            });
        }
        int accepted = 0;
        try {
            List<Future<Packet>> futures = roundExecutor.invokeAll(
                    tasks, PaxosConfig.ACCEPT_PHASE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            for (Future<Packet> future : futures) {
                if (future.isCancelled()) {
                    continue;
                }
                try {
                    Packet response = future.get();
                    if (response != null && "Accepted".equals(response.getResponse())) {
                        accepted++;
                        if (accepted >=strictMajority){
                            commitChosenValue(payload);
                            if (payload.getType() == TYPE.DELETE){
                                return new Response("DELETE: "+ payload.getKey(), Logger.formatTime(System.currentTimeMillis()));
                            }
                            else if(payload.getType() == TYPE.PUT){
                                return new Response("PUT: "+ payload.getKey() + " " + Logger.formatTime(System.currentTimeMillis()),  payload.getValue());
                            }
                            
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Accept phase: peer failed (Paxos still alive on majority)");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }        
        return null;

    }

    private void commitChosenValue(Packet chosen) {
        for (NodeAddress peer : this.peers.snapshot()) {
            try {
                BaseServer stub = transport.lookup(peer);
                stub.Commit(chosen);
            } catch (Exception e) {
                Logger.error("Committing", e);
            }
        }
    }
}
