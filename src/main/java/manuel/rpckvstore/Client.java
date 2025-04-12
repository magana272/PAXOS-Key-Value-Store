package manuel.rpckvstore;

import manuel.rpckvstore.BaseClient;
import manuel.rpckvstore.Node.BaseServer;
import manuel.rpckvstore.Packet.Packet;
import manuel.rpckvstore.Node.Learner.KeyValue;
import org.json.JSONException;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.HashMap;
import java.util.Scanner;


public class Client extends Thread implements BaseClient {
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
        return (BaseServer) r.lookup("Node-"+0);
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
                    Packet p;

                    try {
                        p = new Packet(userText);
                    } catch (JSONException e) {
                        Packet.logMalformedRequest();
                        continue;
                    }
                    switch (p.getType()) {
                        case GET:
                            try {
                                p = stub.Get(p);
                            } catch (RemoteException e) {
                                System.err.println("RemoteException while processing GET");
                                e.printStackTrace();
                            }
                            p.logResponseClient();
                            break;
                        case PUT:
                            try {
                                p = stub.Put(p);
                            } catch (RemoteException e) {
                                System.err.println("RemoteException while processing PUT");
                                e.printStackTrace();
                            }
                            p.logResponseClient();
                            break;
                        case DELETE:
                            try {
                                p = stub.Delete(p);
                            } catch (RemoteException e) {
                                System.err.println("RemoteException while processing DELETE");
                                e.printStackTrace();
                            }
                            p.logResponseClient();
                            break;
                        default:
                            System.err.println("Type not recognized");
                    }
                }
            }
        }
    }

}
