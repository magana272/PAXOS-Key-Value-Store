package manuel.rpckvstore.Node;

import java.io.Serializable;


public class Response implements Serializable{

    public String body;
    public String header;
    public Response(String header, String body){
        this.header = header;
        this.body = body;

    }
    @Override
    public String toString() {
        return body;
    }

}
