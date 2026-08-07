package manuel.rpckvstore.Node.Acceptor;

import manuel.rpckvstore.Node.PaxosConfig;
import manuel.rpckvstore.Packet.Packet;
import manuel.rpckvstore.Packet.Promise;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class PaxosAcceptor {
    private static final class Register {
        private Float promisedSequenceNumber;
        private Float acceptedBallot;
        private Packet acceptedValue;
    }

    private final ConcurrentHashMap<String, Register> registers = new ConcurrentHashMap<>();

    private Register register(String key) {
        return registers.computeIfAbsent(key, k -> new Register());
    }

    public Float promisedSequenceNumber(String key) {
        Register r = registers.get(key);
        if (r == null) {
            return null;
        }
        synchronized (r) {
            return r.promisedSequenceNumber;
        }
    }

    public Promise propose(String key, float id) {
        Register r = register(key);
        synchronized (r) {
            if (r.promisedSequenceNumber != null && id < r.promisedSequenceNumber) {
                return Promise.rejected();
            }
            r.promisedSequenceNumber = id;
            return Promise.of(r.acceptedBallot, r.acceptedValue);
        }
    }

    public Packet accept(float sequenceNumber, Packet packet) {
        if (PaxosConfig.acceptorFailRate > 0
                && ThreadLocalRandom.current().nextFloat() < PaxosConfig.acceptorFailRate) {
            System.out.println("Simulating An Accept Failure");
            return null;
        }
        Register r = register(packet.getKey());
        synchronized (r) {
            if (r.promisedSequenceNumber != null && sequenceNumber < r.promisedSequenceNumber) {
                packet.setResponse("Ignored");
                return packet;
            }
            r.promisedSequenceNumber = sequenceNumber;
            r.acceptedBallot = sequenceNumber;
            r.acceptedValue = packet;
            packet.setResponse("Accepted");
            return packet;
        }
    }

    public void advanceInstance(String key) {
        Register r = registers.get(key);
        if (r == null) {
            return;
        }
        synchronized (r) {
            r.acceptedBallot = null;
            r.acceptedValue = null;
        }
    }
}
