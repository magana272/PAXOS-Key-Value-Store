package manuel.rpckvstore.Node.Learner;

import manuel.rpckvstore.Packet.Packet;
import manuel.rpckvstore.Packet.TYPE;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class PaxosLearner {

    private final KeyValueStore kv;
    private final ExecutorService executor;

    public PaxosLearner(KeyValueStore kv, ExecutorService executor) {
        this.kv = kv;
        this.executor = executor;
    }

    public KeyValueStore store() {
        return kv;
    }

    public Packet apply(Packet packet) {
        TYPE type = packet.getType();
        Callable<Packet> task = switch (type) {
            case GET -> KvTasks.get(kv, packet);
            case PUT -> KvTasks.put(kv, packet);
            case DELETE -> KvTasks.delete(kv, packet);
        };
        Future<Packet> future = executor.submit(task);
        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
