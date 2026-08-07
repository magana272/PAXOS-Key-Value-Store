package manuel.rpckvstore.Node.Learner;

import manuel.rpckvstore.Node.Response;
import manuel.rpckvstore.Packet.Packet;

public final class GetOperation implements StoreOperation {

    @Override
    public Response apply(KeyValueStore kv, Packet packet) {
        return new Response("GET " + packet.getKey(), kv.Get(packet.getKey()));
    }
}
