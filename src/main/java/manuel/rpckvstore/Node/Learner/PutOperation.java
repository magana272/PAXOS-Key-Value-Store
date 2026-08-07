package manuel.rpckvstore.Node.Learner;

import manuel.rpckvstore.Node.Response;
import manuel.rpckvstore.Packet.Packet;

public final class PutOperation implements StoreOperation {

    @Override
    public Response apply(KeyValueStore kv, Packet packet) {
        kv.Put(packet.getKey(), packet.getValue());
        return new Response("PUT " + packet.getKey(), "KEY Value Successfully Set");
    }
}
