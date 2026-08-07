package manuel.rpckvstore.Node.Learner;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class KeyValueStore {

    public static final String MISSING_KEY_SENTINEL = "KEY does not exist";

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.ReadLock kvReadLock = lock.readLock();
    private final ReentrantReadWriteLock.WriteLock kvWriteLock = lock.writeLock();
    private final ConcurrentHashMap<String, String> keyValueHashMap = new ConcurrentHashMap<>();

    public Boolean Put(String key, String value) {
        kvWriteLock.lock();
        try {
            if (keyValueHashMap.containsKey(key)) {
                return false;
            }
            keyValueHashMap.put(key, value);
            return true;
        } finally {
            kvWriteLock.unlock();
        }
    }

    public String Get(String key) {
        kvReadLock.lock();
        try {
            String res = keyValueHashMap.get(key);
            return res != null ? res : MISSING_KEY_SENTINEL;
        } finally {
            kvReadLock.unlock();
        }
    }

    public String Delete(String key) {
        kvWriteLock.lock();
        try {
            return keyValueHashMap.remove(key);
        } finally {
            kvWriteLock.unlock();
        }
    }
}
