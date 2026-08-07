package manuel.rpckvstore.Node.Learner;

import manuel.rpckvstore.Node.Response;
import manuel.rpckvstore.Packet.Packet;

public final class DeleteOperation implements StoreOperation {

    @Override
    public Response apply(KeyValueStore kv, Packet packet) {
        kv.Delete(packet.getKey());
        return new Response("DELETE " + packet.getKey(), "Key-Value Successfully Deleted");
    }
}
