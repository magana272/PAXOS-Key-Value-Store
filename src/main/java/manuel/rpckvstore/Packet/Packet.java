package manuel.rpckvstore.Packet;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.net.InetAddress;

public class Packet implements Serializable {

    private String key;
    private String value;
    private String req;
    private TYPE reqType;
    private String response;

    //  IF Packet is UDP
    private InetAddress address;
    private int port;

    public Packet(String req) {
        this.req = req;
        parseReq(req);
    }

    public Packet(String req, InetAddress address, int port) {
        this.req = req;
        parseReq(req);
        this.address = address;
        this.port = port;
    }

    public int getPort() {
        return port;
    }

    public InetAddress getAddress() {
        return address;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getReq() {
        return req;
    }

    public void setReq(String req) {
        this.req = req;
    }

    public TYPE getType() {
        return reqType;
    }

    public void setType(TYPE type) {
        this.reqType = type;
    }

    public void parseReq(String req) throws JSONException {
        try {
            JSONObject parser = new JSONObject(req);
            this.reqType = TYPE.valueOf(String.valueOf(parser.get("TYPE")));
            switch (reqType) {
                case GET, DELETE -> this.key = String.valueOf(parser.get("KEY"));
                case PUT -> {
                    this.key = String.valueOf(parser.get("KEY"));
                    this.value = String.valueOf(parser.get("VALUE"));
                }
            }
        } catch (Exception e) {
            throw new JSONException(req);
        }
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public static void logMalformedRequest() {
        PacketLogger.log("Malformed Request", "");
    }

    public void logRecievedRequest() {
        PacketLogger.log("Recieved Request", req);
    }

    public void logResponseClient() {
        PacketLogger.log("Recieved Response", response);
    }

    public void logResponseServer() {
        PacketLogger.log("Reponse Sent", response);
    }
}
