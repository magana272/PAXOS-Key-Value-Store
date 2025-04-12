package manuel.rpckvstore.Packet;

import org.json.*;

import java.io.Serializable;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Packet implements Serializable {

    private String key;
    private String value;
    private String req;
    TYPE reqType;
    private String response;

    //  IF Packet is UDP
    private InetAddress address;
    private int port;

    public Packet(String req){
        this.req = req;
        parseReq(req);
    }
    public Packet(String req, InetAddress address, int port ){
        this.req = req;
        parseReq(req);
        this.address = address;
        this.port = port;
    }

    public int getPort() {
        return this.port;
    }

    public InetAddress getAddress(){
        return this.address;
    }




    public String getKey() {
        return key;
    }
    public void setKey(String key) {
        this.key = key;
    }

    // Getter and Setter for value
    public String getValue() {
        return value;
    }
    public void setValue(String value) {
        this.value = value;
    }

    // Getter and Setter for req
    public String getReq() {
        return req;
    }
    public void setReq(String req) {
        this.req = req;
    }
    // Getter and Setter for TYPE

    public TYPE getType() {
        return this.reqType;
    }
    public void setType(TYPE type) {
        this.reqType  = type;
    }
    public void parseReq(String req) throws JSONException{
        try {
            JSONObject parser = new JSONObject(req);
            String type = String.valueOf(parser.get("TYPE"));
            this.reqType = TYPE.valueOf(type);
            switch (this.getType()){
                case GET, DELETE:
                    this.key = String.valueOf(parser.get("KEY"));
                    break;
                case PUT:
                    this.key =  String.valueOf(parser.get("KEY"));
                    this.value = String.valueOf(parser.get("VALUE"));
                    break;
                default:
                    Long currentTime = System.currentTimeMillis();
                    throw new IllegalStateException("Malformed Request:" + System.currentTimeMillis());
            }
        }catch (Exception E){
            throw new JSONException(req);
        }
    }

    public String getResponse(){
        return this.response;
    }

    public void setResponse(String response){
        this.response = response;
    }

    public static void logMalformedRequest(){
        Long currentTime = System.currentTimeMillis();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        Date date = new Date(currentTime);
        String time = simpleDateFormat.format(date);
        System.err.println("Malformed Request " + time);
    }

    public static String formatTime(Long timeLong){
        Long currentTime = timeLong;
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        Date date = new Date(currentTime);
        String time = simpleDateFormat.format(date);
        return time;
    }

    public void logRecievedRequest(){
        Long currentTime = System.currentTimeMillis();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        Date date = new Date(currentTime);
        String time = simpleDateFormat.format(date);
        System.err.println("Recieved Request ( "+time +"):"+ this.getReq());

    }
    public void logResponseClient(){
        Long currentTime = System.currentTimeMillis();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        Date date = new Date(currentTime);
        String time = simpleDateFormat.format(date);
        System.err.println("Recieved Response (" +time +"):"+ this.getResponse() );

    }

    public void logResponseServer(){
        Long currentTime = System.currentTimeMillis();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        Date date = new Date(currentTime);
        String time = simpleDateFormat.format(date);
        System.err.println("Reponse Sent (" +time +"):"+ this.getResponse() );

    }
}
