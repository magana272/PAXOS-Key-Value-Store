package manuel.rpckvstore.Logger;

import java.text.SimpleDateFormat;
import java.util.Date;

import manuel.rpckvstore.Node.Response;
import manuel.rpckvstore.Packet.Packet;

public final class Logger {

    private static final String TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";

    private Logger() {}

    public static String formatTime(long epochMillis) {
        return new SimpleDateFormat(TIMESTAMP_FORMAT).format(new Date(epochMillis));
    }

    public static void log(String label, String body) {
        System.err.println(label + " (" + formatTime(System.currentTimeMillis()) + "): " + body);
    }
    public static void logMalformedRequest() {
        log("Malformed Request", "");
    }
    public static void log(Response r) {
        System.err.println(r.header + " (" + formatTime(System.currentTimeMillis()) + "): " + r.body);
    }
    public static void log(Packet p) {
        Logger.log("Key:" + p.getKey(),"Request: "+ p.getrequest());
    }
    public static void error(String s, Exception e){
        System.out.println(s);
        e.printStackTrace();
        
    }
}
