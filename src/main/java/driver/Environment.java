package driver;

import java.util.HashMap;
import java.util.Map;

public class Environment {
    private final Map<String, String> resourceLookup = new HashMap<>();
    private final Map<String, String> sourceLookup = new HashMap<>();
    private final Map<String, String> paramLookup = new HashMap<>();

    public void putResource(String key, String val) {
        resourceLookup.put(key, val);
    }

    public Map<String, String> getResources() {
        return resourceLookup;
    }
}
