package manuel.rpckvstore.Node.Proposer;

import manuel.rpckvstore.Node.AcceptorRpc;
import manuel.rpckvstore.Node.LearnerRpc;
import manuel.rpckvstore.Node.PaxosConfig;
import manuel.rpckvstore.Node.Response;
import manuel.rpckvstore.Node.cluster.PeerDirectory;
import manuel.rpckvstore.Node.cluster.Transport;
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
    private final Transport transport;

    private final ExecutorService roundExecutor = Executors.newCachedThreadPool();
    private final ConcurrentHashMap<String, Long> sequenceByKey = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> keyLocks = new ConcurrentHashMap<>();

    public PaxosProposer(String nodeId,
                         PeerDirectory peers,
                         Transport transport) {
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
        List<Callable<Promise>> tasks = new ArrayList<>();
        for (NodeAddress peer : this.peers.snapshot()) {
            tasks.add(() -> {
                try {
                    AcceptorRpc stub = this.transport.lookupWithLoss(peer);
                    return stub.Propose(key, proposal);
                } catch (Exception e) {
                    this.transport.invalidate(peer);
                    return Promise.rejected();
                }
            });
        }
        List<Promise> promises = new ArrayList<>();
        try {
            List<Future<Promise>> futures = roundExecutor.invokeAll(
                    tasks, PaxosConfig.PREPARE_PHASE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            for (Future<Promise> future : futures) {
                try {
                    promises.add(future.isCancelled() ? Promise.rejected() : future.get());
                } catch (Exception e) {
                    promises.add(Promise.rejected());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
                    AcceptorRpc stub = transport.lookupWithLoss(peer);
                    return stub.Accept(proposal, payload);
                } catch (Exception e) {
                    transport.invalidate(peer);
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
                        if (accepted >= strictMajority) {
                            commitChosenValue(payload);
                            if (payload.getType() == TYPE.DELETE) {
                                return new Response("DELETE: " + payload.getKey(), Logger.formatTime(System.currentTimeMillis()));
                            } else if (payload.getType() == TYPE.PUT) {
                                return new Response("PUT: " + payload.getKey() + " " + Logger.formatTime(System.currentTimeMillis()), payload.getValue());
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
        List<Callable<Void>> tasks = new ArrayList<>();
        for (NodeAddress peer : this.peers.snapshot()) {
            tasks.add(() -> {
                try {
                    LearnerRpc stub = transport.lookup(peer);
                    stub.Commit(chosen);
                } catch (Exception e) {
                    transport.invalidate(peer);
                    Logger.error("Committing", e);
                }
                return null;
            });
        }
        try {
            roundExecutor.invokeAll(tasks, PaxosConfig.COMMIT_PHASE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
