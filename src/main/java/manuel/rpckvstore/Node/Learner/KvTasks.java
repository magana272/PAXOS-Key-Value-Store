package manuel.rpckvstore.Node.Learner;

import manuel.rpckvstore.Packet.Packet;

import java.util.concurrent.Callable;

public final class KvTasks {

    private KvTasks() {}

    public static Callable<Packet> put(KeyValueStore kv, Packet p) {
        return () -> {
            kv.put(p.getKey(), p.getValue());
            p.setResponse("KEY Value Successfully Set");
            p.logResponseServer();
            return p;
        };
    }

    public static Callable<Packet> get(KeyValueStore kv, Packet p) {
        return () -> {
            p.setResponse(kv.get(p.getKey()));
            p.logResponseServer();
            return p;
        };
    }

    public static Callable<Packet> delete(KeyValueStore kv, Packet p) {
        return () -> {
            kv.delete(p.getKey());
            p.setResponse("Key-Value Successfully Deleted");
            p.logResponseServer();
            return p;
        };
    }
}
