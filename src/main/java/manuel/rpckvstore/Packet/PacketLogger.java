package manuel.rpckvstore.Packet;

import java.text.SimpleDateFormat;
import java.util.Date;

public final class PacketLogger {

    private static final String TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";

    private PacketLogger() {}

    public static String formatTime(long epochMillis) {
        return new SimpleDateFormat(TIMESTAMP_FORMAT).format(new Date(epochMillis));
    }

    public static void log(String label, String body) {
        System.err.println(label + " (" + formatTime(System.currentTimeMillis()) + "): " + body);
    }
}
