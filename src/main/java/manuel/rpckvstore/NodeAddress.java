package manuel.rpckvstore;

import manuel.rpckvstore.Node.BaseServer;

import java.io.Serializable;
import java.rmi.AccessException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;

public class NodeAddress implements Serializable, Comparable<NodeAddress> {
    private String id;
    private String ip;
    private int port;
    public NodeAddress(String id,String IPString, String portString) {
        this.id =id;
        this.ip = IPString;
        this.port = Integer.parseInt(portString);
    }
    public String getIp() {
        return this.ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }
    public String getPort() {
        return String.valueOf(this.port);
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return String.format("NodeAddress [id=" + id + ", ip=" + ip + ", port=" + port + "]");
    }

    @Override
    public int compareTo(NodeAddress o) {
        return Integer.parseInt(o.getId()) - Integer.parseInt(this.id);
    }

    public boolean isAlive() {
        BaseServer stub = null;
        try {
            stub = (BaseServer) LocateRegistry.getRegistry(this.getIp(),Integer.parseInt(this.getPort())).lookup("Node-"+this.getId());
        } catch (NotBoundException | RemoteException e) {
            return false;
        }

        try{
            if (stub == null) {
                return false;
            }
            return stub.isAlive();
        } catch (RemoteException e) {
            return false;
        }

    }
}
