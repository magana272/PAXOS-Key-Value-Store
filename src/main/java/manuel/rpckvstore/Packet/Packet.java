package manuel.rpckvstore.Packet;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.net.InetAddress;

public class Packet implements Serializable {

    private String key;
    private String value;
    private String request;
    private TYPE requestType;
    private String response;

    //  IF Packet is UDP
    private InetAddress address;
    private int port;

    public Packet(String request) {
        this.request = request;
        parserequest(request);
    }

    public Packet(String request, InetAddress address, int port) {
        this.request = request;
        parserequest(request);
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

    public String getrequest() {
        return request;
    }

    public void setrequest(String request) {
        this.request = request;
    }

    public TYPE getType() {
        return requestType;
    }

    public void setType(TYPE type) {
        this.requestType = type;
    }

    public void parserequest(String request) throws JSONException {
        try {
            JSONObject parser = new JSONObject(request);
            this.requestType = TYPE.valueOf(String.valueOf(parser.get("TYPE")));
            switch (requestType) {
                case GET, DELETE -> this.key = String.valueOf(parser.get("KEY"));
                case PUT -> {
                    this.key = String.valueOf(parser.get("KEY"));
                    this.value = String.valueOf(parser.get("VALUE"));
                }
            }
        } catch (Exception e) {
            throw new JSONException(request);
        }
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }
}
