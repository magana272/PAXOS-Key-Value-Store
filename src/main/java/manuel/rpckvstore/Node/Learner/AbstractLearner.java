package manuel.rpckvstore.Node.Learner;

import manuel.rpckvstore.Node.Response;
import manuel.rpckvstore.Packet.Packet;

public abstract class AbstractLearner {

    public abstract Response apply(Packet packet);
}
