package manuel.rpckvstore.Node.Learner;

import java.util.concurrent.ConcurrentHashMap;

public class KeyValueStore {

    public static final String MISSING_KEY_SENTINEL = "KEY does not exist";

    private final ConcurrentHashMap<String, String> keyValueHashMap = new ConcurrentHashMap<>();

    public Boolean Put(String key, String value) {
        keyValueHashMap.put(key, value);
        return true;
    }

    public String Get(String key) {
        String res = keyValueHashMap.get(key);
        return res != null ? res : MISSING_KEY_SENTINEL;
        
    }

    public String Delete(String key) {
        return keyValueHashMap.remove(key);
    }
}
