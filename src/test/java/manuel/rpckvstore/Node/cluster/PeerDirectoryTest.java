package manuel.rpckvstore.Node.cluster;

import manuel.rpckvstore.NodeAddress;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PeerDirectoryTest {

    private static NodeAddress node(String id) {
        return new NodeAddress(id, "127.0.0.1", "1099");
    }

    @Test
    void constructorSeedsWithSelf() {
        PeerDirectory peers = new PeerDirectory(node("1"));

        assertEquals(1, peers.size());
        assertTrue(peers.view().stream().anyMatch(a -> a.getId().equals("1")));
    }

    @Test
    void addAndRemove() {
        PeerDirectory peers = new PeerDirectory(node("1"));
        NodeAddress two = node("2");

        peers.add(two);
        assertEquals(2, peers.size());

        peers.remove(two);
        assertEquals(1, peers.size());
    }

    @Test
    void snapshotIsIndependentCopy() {
        PeerDirectory peers = new PeerDirectory(node("1"));
        Set<NodeAddress> snap = peers.snapshot();

        peers.add(node("2"));

        assertEquals(1, snap.size());
        assertEquals(2, peers.size());
    }

    @Test
    void replaceAllSwapsMembership() {
        PeerDirectory peers = new PeerDirectory(node("1"));
        Set<NodeAddress> updated = new HashSet<>();
        updated.add(node("5"));
        updated.add(node("6"));

        peers.replaceAll(updated);

        assertEquals(2, peers.size());
        assertFalse(peers.view().stream().anyMatch(a -> a.getId().equals("1")));
    }

    @Test
    void viewReflectsLiveState() {
        PeerDirectory peers = new PeerDirectory(node("1"));
        Set<NodeAddress> view = peers.view();

        peers.add(node("9"));

        assertEquals(2, view.size());
    }
}