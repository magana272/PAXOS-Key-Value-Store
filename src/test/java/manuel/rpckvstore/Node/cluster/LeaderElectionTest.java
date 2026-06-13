package manuel.rpckvstore.Node.cluster;

import manuel.rpckvstore.NodeAddress;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class LeaderElectionTest {

    @Test
    void electsHighestNumericId() {
        PeerDirectory peers = new PeerDirectory(new NodeAddress("1", "h", "1"));
        peers.add(new NodeAddress("7", "h", "2"));
        peers.add(new NodeAddress("3", "h", "3"));

        LeaderElection election = new LeaderElection(peers);

        assertEquals("7", election.elect().getId());
        assertEquals("7", election.current().getId());
    }

    @Test
    void demoteClearsCurrentLeader() {
        PeerDirectory peers = new PeerDirectory(new NodeAddress("9", "h", "1"));
        LeaderElection election = new LeaderElection(peers);
        NodeAddress leader = election.elect();

        election.demote(leader);

        assertNull(election.current());
    }
}
