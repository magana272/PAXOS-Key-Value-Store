package manuel.rpckvstore.Node.Learner;

import java.util.HashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class KeyValueStore {

    public static final String MISSING_KEY_SENTINEL = "KEY does not exist";

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.ReadLock kvReadLock = lock.readLock();
    private final ReentrantReadWriteLock.WriteLock kvWriteLock = lock.writeLock();
    private final HashMap<String, String> keyValue = new HashMap<>();

    public boolean put(String key, String value) {
        kvWriteLock.lock();
        try {
            if (keyValue.containsKey(key)) {
                return false;
            }
            keyValue.put(key, value);
            return true;
        } finally {
            kvWriteLock.unlock();
        }
    }

    public String get(String key) {
        kvReadLock.lock();
        try {
            String res = keyValue.get(key);
            return res != null ? res : MISSING_KEY_SENTINEL;
        } finally {
            kvReadLock.unlock();
        }
    }

    public boolean delete(String key) {
        kvWriteLock.lock();
        try {
            return keyValue.remove(key) != null;
        } finally {
            kvWriteLock.unlock();
        }
    }
}
