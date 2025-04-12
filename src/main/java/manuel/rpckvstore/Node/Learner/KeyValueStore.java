package manuel.rpckvstore.Node.Learner;

import java.util.HashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class KeyValueStore {

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.ReadLock kvReadLock = lock.readLock();
    private final ReentrantReadWriteLock.ReadLock kvWriteLock = lock.readLock();
    protected HashMap<String, String> keyValue;

    public KeyValueStore() {
        this.keyValue = new HashMap<>();
    }

    public boolean put(String key, String value) {
        if (this.keyValue.containsKey(key)) {
            return false;
        } else {
            kvWriteLock.lock();
            this.keyValue.put(key, value);
            kvWriteLock.unlock();
            return true;
        }
    }

    public String get(String key) {
        if (this.keyValue.get(key) != null) {
            kvReadLock.lock();
            String res = this.keyValue.get(key);
            kvReadLock.unlock();
            return res;


        } else {
            System.out.println("KEY does not exist");
            return "KEY does not exist";
        }
    }

    public boolean delete(String key) {
        if (this.keyValue.containsKey(key)) {
            kvWriteLock.lock();
            this.keyValue.remove(key);
            kvWriteLock.unlock();
            return true;
        } else {
            return false;
        }

    }
}
