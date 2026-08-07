package manuel.rpckvstore.Node.Learner;

import manuel.rpckvstore.Node.Response;
import manuel.rpckvstore.Packet.Packet;

public interface StoreOperation {

    Response apply(KeyValueStore kv, Packet packet);
}
