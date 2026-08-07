package manuel.rpckvstore;

import manuel.rpckvstore.Node.BaseServer;
import manuel.rpckvstore.Node.Response;
import manuel.rpckvstore.Packet.Packet;
import org.json.JSONException;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Scanner;
import manuel.rpckvstore.Logger.Logger;


public class Client {
    private Registry cRegistry;
    private String HostNameorIPAddress;
    private int Portnumber;

    public String getPortnumber() {
        return String.valueOf(this.Portnumber);
    }

    public Client(String HostNameorIPAddres, int PortNumber) throws RemoteException {
        this.HostNameorIPAddress = HostNameorIPAddres;
        this.cRegistry = LocateRegistry.getRegistry(this.HostNameorIPAddress,1099);
        this.Portnumber = PortNumber;
    }
    public Registry getRegistry() {
        return this.cRegistry;
    }

    public final BaseServer getStub() throws NotBoundException, RemoteException {
        System.out.println("Get stub");
        Registry r = this.getRegistry();
        // Each node binds itself in its own registry as "Node-<id>", so the
        // name is not always "Node-0". Discover whichever node this registry
        // hosts so the client can connect to any node, not just node0.
        for (String name : r.list()) {
            if (name.startsWith("Node-")) {
                return (BaseServer) r.lookup(name);
            }
        }
        throw new NotBoundException("No Node-* bound in registry at " + this.HostNameorIPAddress);
    }



    public static void main(String[] args) {
        // Ensure correct number of arguments
        if (args.length < 2) {
            System.err.println("Port Number and IP Address Must be Provided");
            System.exit(1);
        }

        String IPString = args[0];  // Use provided IP
        String PortString = args[1]; // Use provided Port
        int PortNumber;

        // Convert port string to int safely
        try {
            PortNumber = Integer.parseInt(PortString);
        } catch (NumberFormatException e) {
            System.err.println("Invalid port number: " + PortString);
            System.exit(1);
            return;
        }

        Client c = null;
        BaseServer stub = null;

        // Establish client connection
        try {
            System.out.println("Attempting to connect to server at " + IPString + ":" + PortNumber);
            c = new Client(IPString, PortNumber);
            stub = c.getStub();
            System.out.println("Connected successfully!");
        } catch (RemoteException e) {
            System.err.println("Could not establish Client. The port could be busy");
            e.printStackTrace();
            System.exit(0);
        } catch (NotBoundException e) {
            System.err.println("Couldn't connect to server at " + IPString + ":" + PortNumber);
            e.printStackTrace();
            System.exit(0);
        }

        Example ex = new Example(stub);
        ex.runExample();

        // Read user input
        try (Scanner userInput = new Scanner(System.in)) {
            while (true) {
                if (userInput.hasNext()) {
                    String userText = userInput.nextLine();
                    Response response;
                    Packet p;

                    try {
                        p = new Packet(userText);
                    } catch (JSONException e) {
                        Logger.logMalformedRequest();
                        continue;
                    }
                    switch (p.getType()) {
                        case GET:
                            try {
                                response = stub.Get(p);
                                Logger.log(response);
                            } catch (RemoteException e) {
                                System.err.println("RemoteException while processing GET");
                                e.printStackTrace();
                            }
                            break;
                        case PUT:
                            try {
                                response = stub.Put(p);
                                Logger.log(response);

                            } catch (RemoteException e) {
                                System.err.println("RemoteException while processing PUT");
                                e.printStackTrace();
                            }
                            
                            break;
                        case DELETE:
                            try {
                                response = stub.Delete(p);
                                Logger.log(response);
                            } catch (RemoteException e) {
                                System.err.println("RemoteException while processing DELETE");
                                e.printStackTrace();
                            }
                            
                            break;
                        default:
                            System.err.println("Type not recognized");
                    }
                }
            }
        }
    }

}
