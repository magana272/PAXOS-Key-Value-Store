package manuel.rpckvstore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GenComposeScriptTest {

    @BeforeEach
    void requiresBashAndScript() {
        assumeTrue(!System.getProperty("os.name").toLowerCase().startsWith("windows"));
        assumeTrue(new File("scripts/gen-compose.sh").exists());
    }

    @Test
    void emitsClusterScopedVolumesEnvAndServices() throws Exception {
        ProcessBuilder pb = new ProcessBuilder("bash", "scripts/gen-compose.sh");
        pb.redirectErrorStream(true);
        pb.environment().put("CLUSTER_NAME", "testcluster");
        pb.environment().put("CLUSTER_SIZE", "3");
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, p.waitFor(), "gen-compose.sh exited non-zero:\n" + out);

        assertTrue(out.contains("./logs/testcluster/node0:/app/logs/testcluster/node0"),
                "missing cluster-scoped volume:\n" + out);
        assertTrue(out.contains("LOG_DIR: \"/app/logs/testcluster/node0\""),
                "missing scoped LOG_DIR:\n" + out);
        assertTrue(out.contains("CLUSTER_NAME: \"testcluster\""),
                "missing CLUSTER_NAME env:\n" + out);
        assertEquals(3, countServices(out), "expected exactly 3 node services:\n" + out);
    }

    @Test
    void rejectsInvalidClusterName() throws Exception {
        ProcessBuilder pb = new ProcessBuilder("bash", "scripts/gen-compose.sh");
        pb.redirectErrorStream(true);
        pb.environment().put("CLUSTER_NAME", "Bad Name");
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertNotEquals(0, p.waitFor(), "expected non-zero exit for invalid name:\n" + out);
        assertTrue(out.contains("CLUSTER_NAME must match"), "missing validation error:\n" + out);
    }

    private static int countServices(String text) {
        Matcher m = Pattern.compile("(?m)^  node\\d+:").matcher(text);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }
}
