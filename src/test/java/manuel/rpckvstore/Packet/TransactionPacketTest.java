package manuel.rpckvstore.Packet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TransactionPacketTest {

    private static Packet anyPacket() {
        return new Packet("{\"TYPE\":\"PUT\",\"KEY\":\"k\",\"VALUE\":\"v\"}");
    }

    @Test
    public void voteParameterIsHonoured() throws Exception {
        TransactionPacket no = new TransactionPacket(anyPacket(), Vote.NO);
        TransactionPacket yes = new TransactionPacket(anyPacket(), Vote.YES);

        assertEquals(Vote.NO, no.getVote());
        assertEquals(Vote.YES, yes.getVote());
    }

    @Test
    public void singleArgConstructorDefaultsToYes() throws Exception {
        assertEquals(Vote.YES, new TransactionPacket(anyPacket()).getVote());
    }
}
