package manuel.rpckvstore;

import manuel.rpckvstore.Node.BaseServer;
import manuel.rpckvstore.Packet.Packet;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

public class LoadClient {

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("Usage: java LoadClient <host> <port> <threads> <opsPerThread> [PUT|GET]");
            System.exit(1);
        }
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        int threads = Integer.parseInt(args[2]);
        int opsPerThread = Integer.parseInt(args[3]);
        String op = args.length > 4 ? args[4].toUpperCase() : "PUT";

        Registry registry = LocateRegistry.getRegistry(host, port);
        BaseServer stub = (BaseServer) registry.lookup("Node-0");

        int totalOps = threads * opsPerThread;
        AtomicLong okCount = new AtomicLong();
        AtomicLong errCount = new AtomicLong();
        long[] latencies = new long[totalOps];   // nanos per op; <0 means error
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            final int tid = t;
            Thread worker = new Thread(() -> {
                String base = "load-" + tid + "-";
                ready.countDown();
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                for (int j = 0; j < opsPerThread; j++) {
                    String key = base + j;                 // distinct key per op
                    long s = System.nanoTime();
                    try {
                        if ("GET".equals(op)) {
                            stub.Get(new Packet("{TYPE:GET,KEY:" + key + "}"));
                        } else {
                            stub.Put(new Packet("{TYPE:PUT,KEY:" + key + ",VALUE:v" + j + "}"));
                        }
                        latencies[tid * opsPerThread + j] = System.nanoTime() - s;
                        okCount.incrementAndGet();
                    } catch (Exception e) {
                        latencies[tid * opsPerThread + j] = -1;
                        errCount.incrementAndGet();
                    }
                }
                done.countDown();
            }, "load-" + tid);
            worker.start();
        }

        ready.await();                       // all workers built and waiting
        long wallStart = System.nanoTime();
        start.countDown();                   // release them together
        done.await();
        double wallSec = (System.nanoTime() - wallStart) / 1_000_000_000.0;

        long ok = okCount.get();
        List<Long> okLat = new ArrayList<>();
        for (long l : latencies) {
            if (l >= 0) {
                okLat.add(l);
            }
        }
        Collections.sort(okLat);

        System.out.printf("threads=%d opsPerThread=%d op=%s%n", threads, opsPerThread, op);
        System.out.printf("total=%d ok=%d err=%d wall=%.3fs%n", totalOps, ok, errCount.get(), wallSec);
        System.out.printf("THROUGHPUT = %.1f ops/sec%n", wallSec > 0 ? ok / wallSec : 0.0);
        if (!okLat.isEmpty()) {
            System.out.printf("latency ms: p50=%.2f p95=%.2f p99=%.2f max=%.2f%n",
                    pct(okLat, 50) / 1e6, pct(okLat, 95) / 1e6,
                    pct(okLat, 99) / 1e6, okLat.get(okLat.size() - 1) / 1e6);
        }
    }

    private static double pct(List<Long> sorted, double p) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int idx = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        idx = Math.max(0, Math.min(idx, sorted.size() - 1));
        return sorted.get(idx);
    }
}
