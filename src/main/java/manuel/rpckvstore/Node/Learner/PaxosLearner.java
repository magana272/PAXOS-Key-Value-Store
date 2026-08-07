package manuel.rpckvstore.Node.Learner;

import manuel.rpckvstore.Logger.Logger;
import manuel.rpckvstore.Node.Response;
import manuel.rpckvstore.Packet.Packet;
import manuel.rpckvstore.Packet.TYPE;

import java.util.EnumMap;
import java.util.Map;

public class PaxosLearner extends AbstractLearner {

    private final KeyValueStore kv;

    private final Map<TYPE, StoreOperation> operations = new EnumMap<>(TYPE.class);

    public PaxosLearner(KeyValueStore kv) {
        this.kv = kv;
        operations.put(TYPE.PUT, new PutOperation());
        operations.put(TYPE.GET, new GetOperation());
        operations.put(TYPE.DELETE, new DeleteOperation());
    }

    public KeyValueStore store() {
        return kv;
    }

    @Override
    public Response apply(Packet packet) {
        StoreOperation operation = operations.get(packet.getType());
        Response response = (operation != null)
                ? operation.apply(kv, packet)
                : new Response(null, null);
        Logger.log(packet);
        return response;
    }
}
