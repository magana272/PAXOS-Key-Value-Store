package manuel.rpckvstore.Logger;

import manuel.rpckvstore.Node.Response;
import manuel.rpckvstore.Packet.Packet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LoggerTest {

    @Test
    void formatTimeIsStableForFixedEpoch() {
        String formatted = Logger.formatTime(0L);

        assertEquals(23, formatted.length());
        assertTrue(formatted.startsWith("19") || formatted.startsWith("20"));
    }

    @Test
    void logLabelBodyDoesNotThrow() {
        assertDoesNotThrow(() -> Logger.log("label", "body"));
    }

    @Test
    void logMalformedRequestDoesNotThrow() {
        assertDoesNotThrow(Logger::logMalformedRequest);
    }

    @Test
    void logResponseDoesNotThrow() {
        assertDoesNotThrow(() -> Logger.log(new Response("h", "b")));
    }

    @Test
    void logPacketDoesNotThrow() {
        Packet p = new Packet("{\"TYPE\":\"GET\",\"KEY\":\"k\"}");

        assertDoesNotThrow(() -> Logger.log(p));
    }

    @Test
    void errorDoesNotThrow() {
        assertDoesNotThrow(() -> Logger.error("boom", new RuntimeException("x")));
    }
}