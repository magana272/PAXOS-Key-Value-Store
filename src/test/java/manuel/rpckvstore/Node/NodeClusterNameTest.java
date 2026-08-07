package manuel.rpckvstore.Node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class NodeClusterNameTest {

    @Test
    void explicitClusterNameIsStored() throws Exception {
        Node node = new Node("cn-1", "localhost", "1099", 1099, 0f, 0f, "alpha-cluster");
        assertEquals("alpha-cluster", node.getClusterName());
    }

    @Test
    void defaultClusterNameMatchesEnvironmentOrDefault() throws Exception {
        Node node = new Node("cn-2", "localhost", "1099", 1099, 0f, 0f);
        String expected = System.getenv().getOrDefault("CLUSTER_NAME", "default");
        assertEquals(expected, node.getClusterName());
    }

    @Test
    void defaultLogDirIsScopedByClusterName() throws Exception {
        assumeTrue(System.getenv("LOG_DIR") == null);
        String cluster = "cn-test-" + UUID.randomUUID().toString().substring(0, 8);
        String id = "cn-node-" + UUID.randomUUID().toString().substring(0, 8);
        File logFile = new File("logs/" + cluster + "/" + id, "log.txt");
        try {
            new Node(id, "localhost", "1099", 1099, 0f, 0f, cluster);
            assertTrue(logFile.exists(), "expected commit log at " + logFile.getPath());
        } finally {
            logFile.delete();
            logFile.getParentFile().delete();
            new File("logs/" + cluster).delete();
        }
    }
}
