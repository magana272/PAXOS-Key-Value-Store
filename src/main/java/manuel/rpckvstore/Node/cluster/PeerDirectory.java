package manuel.rpckvstore.Node.cluster;

import manuel.rpckvstore.NodeAddress;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class PeerDirectory {

    private Set<NodeAddress> peers = Collections.synchronizedSet(new HashSet<>());

    public PeerDirectory(NodeAddress self) {
        peers.add(self);
    }

    public synchronized Set<NodeAddress> snapshot() {
        return new HashSet<>(peers);
    }

    public synchronized void add(NodeAddress address) {
        peers.add(address);
    }

    public synchronized void remove(NodeAddress address) {
        peers.remove(address);
    }

    public synchronized void replaceAll(Set<NodeAddress> updated) {
        this.peers = Collections.synchronizedSet(new HashSet<>(updated));
    }

    public synchronized int size() {
        return peers.size();
    }

    public synchronized Set<NodeAddress> view() {
        return peers;
    }
}
