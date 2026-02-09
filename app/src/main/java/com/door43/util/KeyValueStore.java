package com.door43.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by joel on 1/14/2015.
 */
public class KeyValueStore {
    private final Map<String, Object> dataStore = new HashMap<String, Object>();

    /**
     * Add a value to the store
     * @param key Key
     * @param value Value
     */
    public void add(String key, Object value) {
        dataStore.put(key, value);
    }

    /**
     * Retrieve a value as a string
     * @param key Key
     * @return String
     */
    public String getString(String key) {
        if(dataStore.containsKey(key)) {
            Object value = dataStore.get(key);
            if (value != null) {
                return value.toString();
            }
        }
        return null;
    }

    /**
     * Retrieve a value as an int
     * @param key Key
     */
    public int getInt(String key) {
        if(dataStore.containsKey(key)) {
            Object value = dataStore.get(key);
            if (value != null) {
                try {
                    return Integer.parseInt(value.toString());
                } catch (Exception ignored) {
                }
            }
        }
        return 0;
    }

    /**
     * Retrieve a value
     * @param key Key
     * @return Object
     */
    public Object get(String key) {
        if(dataStore.containsKey(key)) {
            return dataStore.get(key);
        } else {
            return null;
        }
    }

    public boolean getBool(String key) {
        if(dataStore.containsKey(key)) {
            Object value = dataStore.get(key);
            if (value != null) {
                return Boolean.parseBoolean(value.toString());
            }
        }
        return false;
    }
}
