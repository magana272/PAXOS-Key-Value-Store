package manuel.rpckvstore;

import manuel.rpckvstore.Node.BaseServer;
import manuel.rpckvstore.Node.Response;
import manuel.rpckvstore.Packet.Packet;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Non-interactive validation harness for the Paxos KV cluster.
 *
 * Modeled on Client.java / Example.java, but instead of an interactive REPL
 * it runs a fixed workload against a single target node and prints one CSV
 * row per operation to stdout:
 *
 *   phase,op,key,success,match,latency_ms,error
 *
 * scripts/validate.sh drives this: it brings the cluster up, kills N nodes,
 * then invokes this class against a surviving node and captures stdout into
 * metrics/trial<i>_loss<L>_<phase>.csv (e.g. trial1_loss0_sweep_f3.csv).
 * validate-metrics.py aggregates those CSVs across trials / loss conditions /
 * failure-counts into figures.
 *
 * Usage:
 *   java manuel.rpckvstore.Validate <host> <port> <phase-label> <failedCount> [repeats] [nodeId]
 *
 * Single-op mode (used by paxos-validate.py for concurrent safety/saturation runs):
 *   java manuel.rpckvstore.Validate <host> <port> op <failedCount> <repeats> <nodeId> <PUT|GET|DELETE> <key> [value]
 *   Issues exactly one operation (repeated <repeats> times) with an arbitrary
 *   key/value instead of the fixed fixture, so many concurrent clients can write
 *   distinct values to the same key. Every row carries a resp_hash column (first
 *   12 hex of SHA-1 over the response) so the aggregator can group reads into
 *   exact value-equality classes without dumping ~700-char payloads.
 *
 *   host/port   - RMI registry of a node that is still alive (e.g. node0 1099)
 *   phase-label - free-text tag written into every row (e.g. "seed", "verify")
 *   failedCount - how many nodes the orchestrator killed before this run
 *                 (just metadata, echoed into every row so the aggregator
 *                 can group by it)
 *   repeats     - how many full passes over the 6-key fixture to run
 *                 (default 1; use >1 at failedCount=0 to get a throughput
 *                 sample -- wall-clock ops/sec, not just per-op latency)
 *   nodeId      - defaults to the trailing digits of host (node3 -> 3)
 */
public class Validate {
    
    // Same fixture data as Example.java so seed/verify runs are comparable.
    private static final Map<String, String> FIXTURE = new LinkedHashMap<>();

    static {
    // PUT operations
    FIXTURE.put("user1", "Alice Johnson");
    FIXTURE.put("user2", "Bob Smith");
    FIXTURE.put("configleader", "node0");
    FIXTURE.put("product1", "Mechanical Keyboard");

    // More PUTs
    FIXTURE.put("city1", "San Francisco");
    FIXTURE.put("city2", "New York");

    for (int i = 0; i < 100; i++) {
        FIXTURE.put("test-key" + i, "test-value-" + i);
    }
}

    private static final String CSV_HEADER = "phase,op,key,success,match,latency_ms,failed_count,error,resp_hash";

    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("Usage: java manuel.rpckvstore.Validate <host> <port> <phaseLabel> <failedCount> [repeats] [nodeId]");
            System.err.println("  or:  java manuel.rpckvstore.Validate <host> <port> op <failedCount> <repeats> <nodeId> <PUT|GET|DELETE> <key> [value]");
            System.err.println("  repeats defaults to 1; use >1 (at failedCount=0) for a throughput sample");
            System.err.println("  nodeId defaults to the last path segment of host (e.g. node3 -> 3), or 0 if it can't be parsed");
            System.exit(1);
        }

        String host = args[0];
        int port = Integer.parseInt(args[1]);
        String phaseLabel = args[2];
        String failedCount = args[3];
        int repeats = args.length > 4 ? Math.max(1, Integer.parseInt(args[4])) : 1;
        String nodeId = args.length > 5 ? args[5] : inferNodeId(host);

        System.out.println(CSV_HEADER);

        BaseServer stub;
        try {
            stub = connect(host, port, nodeId);
        } catch (Exception e) {
            emit(phaseLabel, "connect", "-", false, false, 0, failedCount, e.getClass().getSimpleName() + ": " + e.getMessage(), "");
            return;
        }
        if ("op".equals(phaseLabel)) {
            if (args.length < 8) {
                emit(phaseLabel, "op", "-", false, false, 0, failedCount,
                        "op mode needs <PUT|GET|DELETE> <key> [value] after nodeId", "");
                return;
            }
            String opType = args[6];
            String key = args[7];
            String value = args.length > 8 ? args[8] : null;
            for (int pass = 0; pass < repeats; pass++) {
                runOp(stub, phaseLabel, opType, key, value, failedCount);
            }
            return;
        }

        for (int pass = 0; pass < repeats; pass++) {
            if ("seed".equals(phaseLabel) || "load".equals(phaseLabel)) {
                for (Map.Entry<String, String> kv : FIXTURE.entrySet()) {
                    runPut(stub, phaseLabel, kv.getKey(), kv.getValue(), failedCount);
                }
            }
            if ("verify".equals(phaseLabel) || "load".equals(phaseLabel)) {
                for (Map.Entry<String, String> kv : FIXTURE.entrySet()) {
                    runGet(stub, phaseLabel, kv.getKey(), kv.getValue(), failedCount);
                }
            }
            if ("sweep".equals(phaseLabel)) {
                for (Map.Entry<String, String> kv : FIXTURE.entrySet()) {
                    String freshValue = kv.getValue() + "-f" + failedCount;
                    runPut(stub, phaseLabel, kv.getKey(), freshValue, failedCount);
                    runGet(stub, phaseLabel, kv.getKey(), freshValue, failedCount);
                    runDelete(stub, phaseLabel, kv.getKey(), failedCount);
                }
            }
            if ("load".equals(phaseLabel)) {
                for (String key : FIXTURE.keySet()) {
                    runDelete(stub, phaseLabel, key, failedCount);
                }
            }
        }

        if ("load".equals(phaseLabel)) {          
            for (Map.Entry<String, String> kv : FIXTURE.entrySet()) {
                runPut(stub, phaseLabel, kv.getKey(), kv.getValue(), failedCount);
            }
        }
    }

    private static BaseServer connect(String host, int port, String nodeId) throws RemoteException, NotBoundException {
        Registry registry = LocateRegistry.getRegistry(host, port);
        return (BaseServer) registry.lookup("Node-" + nodeId);
    }

    private static void runOp(BaseServer stub, String phase, String opType, String key, String value, String failedCount) {
        String op = opType == null ? "" : opType.toUpperCase();
        switch (op) {
            case "PUT" -> {
                if (value == null) {
                    emit(phase, "PUT", key, false, false, 0, failedCount, "PUT needs a value", "");
                } else {
                    runPut(stub, phase, key, value, failedCount);
                }
            }
            case "GET" -> runGet(stub, phase, key, value, failedCount);
            case "DELETE" -> runDelete(stub, phase, key, failedCount);
            default -> emit(phase, op, key, false, false, 0, failedCount, "unknown op: " + opType, "");
        }
    }

    private static void runPut(BaseServer stub, String phase, String key, String value, String failedCount) {
        Packet req = new Packet(String.format("{TYPE:PUT,KEY:%s,VALUE:%s}", key, value));
        long start = System.nanoTime();
        try {
            Response resp = stub.Put(req);
            double elapsedMs = (System.nanoTime() - start) / 1_000_000.0;
            boolean success = resp != null;
            emit(phase, "PUT", key, success, success, elapsedMs, failedCount, "",
                    hash12(resp == null ? "" : resp.toString()));
        } catch (RemoteException e) {
            double elapsedMs = (System.nanoTime() - start) / 1_000_000.0;
            emit(phase, "PUT", key, false, false, elapsedMs, failedCount, e.getClass().getSimpleName() + ": " + safe(e.getMessage()), "");
        }
    }

    private static void runGet(BaseServer stub, String phase, String key, String expectedValue, String failedCount) {
        Packet req = new Packet(String.format("{TYPE:GET,KEY:%s}", key));
        long start = System.nanoTime();
        try {
            Response resp = stub.Get(req);
            double elapsedMs = (System.nanoTime() - start) / 1_000_000.0;
            boolean success = resp != null;
            String respValue = success ? resp.toString() : null;
            boolean match = success && expectedValue != null && expectedValue.equals(respValue);
            emit(phase, "GET", key, success, match, elapsedMs, failedCount, "",
                    hash12(respValue == null ? "" : respValue));
        } catch (RemoteException e) {
            double elapsedMs = (System.nanoTime() - start) / 1_000_000.0;
            emit(phase, "GET", key, false, false, elapsedMs, failedCount, e.getClass().getSimpleName() + ": " + safe(e.getMessage()), "");
        }
    }

    private static void runDelete(BaseServer stub, String phase, String key, String failedCount) {
        Packet req = new Packet(String.format("{TYPE:DELETE,KEY:%s}", key));
        long start = System.nanoTime();
        try {
            Response resp = stub.Delete(req);
            double elapsedMs = (System.nanoTime() - start) / 1_000_000.0;
            boolean success = resp != null;
            emit(phase, "DELETE", key, success, success, elapsedMs, failedCount, "",
                    hash12(resp == null ? "" : resp.toString()));
        } catch (RemoteException e) {
            double elapsedMs = (System.nanoTime() - start) / 1_000_000.0;
            emit(phase, "DELETE", key, false, false, elapsedMs, failedCount, e.getClass().getSimpleName() + ": " + safe(e.getMessage()), "");
        }
    }

    private static void emit(String phase, String op, String key, boolean success, boolean match,
                              double latencyMs, String failedCount, String error, String respHash) {
        System.out.printf(Locale.US, "%s,%s,%s,%b,%b,%.3f,%s,\"%s\",%s%n",
                phase, op, key, success, match, latencyMs, failedCount, error.replace("\"", "'"), respHash);
    }
    private static String hash12(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest((s == null ? "" : s).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                hex.append(String.format("%02x", digest[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            return "nohash";
        }
    }

    private static String inferNodeId(String host) {
        // host is typically "node3" -> "3"; fall back to "0" (the init node)
        StringBuilder digits = new StringBuilder();
        for (int i = host.length() - 1; i >= 0; i--) {
            char c = host.charAt(i);
            if (Character.isDigit(c)) {
                digits.insert(0, c);
            } else if (digits.length() > 0) {
                break;
            }
        }
        return digits.length() > 0 ? digits.toString() : "0";
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
