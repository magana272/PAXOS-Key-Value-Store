package manuel.rpckvstore;

import manuel.rpckvstore.Node.BaseServer;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;

public class NodeAddress implements Serializable, Comparable<NodeAddress> {
    private static final int PROBE_TIMEOUT_MS = 1000;

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
        try (Socket probe = new Socket()) {
            probe.connect(new InetSocketAddress(this.ip, this.port), PROBE_TIMEOUT_MS);
        } catch (IOException e) {
            return false;
        }

        try {
            BaseServer stub = (BaseServer) LocateRegistry
                    .getRegistry(this.ip, this.port)
                    .lookup("Node-" + this.getId());
            return stub != null && stub.isAlive();
        } catch (NotBoundException | RemoteException e) {
            return false;
        }
    }
}
