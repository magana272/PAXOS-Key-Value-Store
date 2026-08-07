package manuel.rpckvstore.Node.Learner;

import manuel.rpckvstore.Logger.Logger;
import manuel.rpckvstore.Node.Response;
import manuel.rpckvstore.Packet.Packet;

public class PaxosLearner {

    private final KeyValueStore kv;

    public PaxosLearner(KeyValueStore kv) {
        this.kv = kv;
    }

    public KeyValueStore store() {
        return kv;
    }

    public Response apply(Packet packet) {
        Response response;
        switch (packet.getType()) {
            case PUT -> {
                kv.Put(packet.getKey(), packet.getValue());
                response = new Response("PUT " + packet.getKey(), "KEY Value Successfully Set");
            }
            // A GET carries no value on the packet; the value lives in the store.
            case GET -> response = new Response("GET " + packet.getKey(), kv.Get(packet.getKey()));
            case DELETE -> {
                kv.Delete(packet.getKey());
                response = new Response("DELETE " + packet.getKey(), "Key-Value Successfully Deleted");
            }
            default -> response = new Response(null, null);
        }
        Logger.log(packet);
        return response;
    }
}
