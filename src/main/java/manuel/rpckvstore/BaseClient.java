package manuel.rpckvstore;

import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.Registry;

public interface BaseClient extends Remote {
    public Registry getRegistry();
    public Remote getStub() throws NotBoundException, RemoteException;
}
