package manuel.rpckvstore;

import manuel.rpckvstore.Node.BaseServer;
import manuel.rpckvstore.Node.Response;
import manuel.rpckvstore.Packet.Packet;

import java.rmi.RemoteException;
import manuel.rpckvstore.Logger.Logger;


public class PutGetDeleteThread implements Runnable {

    private static final String MISSING_KEY = "KEY does not exist";

    private final String protienID;
    private final String sequence;
    private final BaseServer stub;
    private volatile boolean success = false;

    PutGetDeleteThread(BaseServer stub, String protienID, String sequence) {
        this.stub = stub;
        this.protienID = protienID;
        this.sequence = sequence;
    }

    public boolean isSuccess() {
        return success;
    }

    @Override
    public void run() {
        try {
            Response put = stub.Put(new Packet(
                    String.format("{TYPE:PUT,KEY:%s,VALUE:%s}", protienID, sequence)));
            Logger.log(put);

            Response got = stub.Get(new Packet(
                    String.format("{TYPE:GET,KEY:%s}", protienID)));
            Logger.log(got);
            boolean readBack = got != null && sequence.equals(got.toString());

            Response deleted = stub.Delete(new Packet(
                    String.format("{TYPE:DELETE,KEY:%s}", protienID)));
            Logger.log(deleted);

            Response afterDelete = stub.Get(new Packet(
                    String.format("{TYPE:GET,KEY:%s}", protienID)));
            boolean removed = afterDelete != null && MISSING_KEY.equals(afterDelete.toString());

            success = readBack && removed;
            System.out.println("[" + protienID + "] " + (success ? "PASS" : "FAIL")
                    + " (read-back=" + readBack + ", deleted=" + removed + ")");
        } catch (RemoteException e) {
            System.err.println("[" + protienID + "] FAIL: " + e.getMessage());
        }
    }
}
