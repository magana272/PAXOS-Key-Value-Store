package manuel.rpckvstore.Node; // use your actual package here

import java.rmi.registry.Registry;

public class LocateRegistryMockHelper {
    private static Registry registry;

    public static void setMockRegistry(Registry mock) {
        registry = mock;
    }

    public static Registry getRegistry(String ip, int port) {
        return registry;
    }
}
