package org.unfoldingword.resourcecontainer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A utility for reading nested objects without pulling your hair out.
 *
 * Supported objects: List, Map, JSONObject, JSONArray
 *
 */
class ObjectReader implements Iterable<ObjectReader> {
    private final Object map;

    /**
     * The map or value
     * @param obj the object to be read.
     */
    ObjectReader(Object obj) {
        this.map = obj;
    }

    /**
     * Resolves a new instance of the reader with the value.
     *
     * @param key the key to look up
     * @return an instance of the reader with the value
     */
    public ObjectReader get(Object key) {
        if(this.map instanceof Map && ((Map)this.map).containsKey(key)) {
            // map
            return new ObjectReader(((Map) this.map).get(key));
        } else if (key instanceof String
                && this.map instanceof JSONObject
                && ((JSONObject) this.map).has((String)key)) {
            // json object
            Object value = null;
            try {
                value = ((JSONObject) this.map).get((String)key);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return new ObjectReader(value);
        } else if (key instanceof Integer
                && this.map instanceof JSONArray
                && (Integer) key >= 0
                && ((JSONArray)this.map).length() > (Integer)key) {
            // json array
            Object value = null;
            try {
                value = ((JSONArray) this.map).get((Integer) key);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return new ObjectReader(value);
        } else if (key instanceof Integer
                && this.map instanceof List
                && (Integer) key >= 0
                && ((List)this.map).size() > (Integer)key) {
            // list
            return new ObjectReader(((List)this.map).get((Integer)key));
        } else {
            return new ObjectReader(null);
        }
    }

    /**
     * Returns the keys in the reader
     *
     * @return a list of keys
     */
    List<Object> keys() {
        if(this.map instanceof Map) {
            return new ArrayList<Object>(((Map) this.map).keySet());
        } else if(this.map instanceof Collection) {
            int size = ((Collection) this.map).size();
            List<Object> keys = new ArrayList<>();
            for(int i = 0; i < size; i ++) {
                keys.add(i);
            }
            return keys;
        } else if(this.map instanceof JSONArray) {
            int size = ((JSONArray) this.map).length();
            List<Object> keys = new ArrayList<>();
            for(int i = 0; i < size; i ++) {
                keys.add(i);
            }
            return keys;
        } else if(this.map instanceof JSONObject) {
            List<Object> keys = new ArrayList<>();
            Iterator<String> iter = ((JSONObject) this.map).keys();
            while(iter.hasNext()) keys.add(iter.next());
            return keys;
        } else {
            return new ArrayList<>();
        }
    }

    /**
     * Returns the value of the reader
     * @return the value
     */
    public Object value() {
        if(this.map != null) return this.map;
        return null;
    }

    /**
     * Returns the size of the object being read if it is a map or collection.
     * If the reader contains anything else this will return 0.
     *
     * @return the size of the map
     */
    public int size() {
        if(this.map instanceof Map) {
            return ((Map) this.map).size();
        } else if(this.map instanceof Collection) {
            return ((Collection) this.map).size();
        } else if(this.map instanceof JSONArray) {
            return ((JSONArray) this.map).length();
        } else if(this.map instanceof JSONObject) {
            return ((JSONObject) this.map).length();
        } else {
            return 0;
        }
    }

    /**
     * Convenience method for checking of the value/map is null
     * @return true if the value or map is null
     */
    public boolean isNull() {
        return this.map == null;
    }

    /**
     * Returns the string value of the reader.
     * An empty string will be returned if the reader value is null;
     * @return the string value
     */
    @Override
    public String toString() {
        if(this.map != null) return this.map.toString();
        return "";
    }

    @Override
    public Iterator<ObjectReader> iterator() {
        return new ObjectIterator(this);
    }

    /**
     * Utility class for iterating over a reader
     */
    private class ObjectIterator implements Iterator<ObjectReader> {

        private final ObjectReader reader;
        private final List<Object> keys;
        private int index = 0;

        private ObjectIterator(ObjectReader reader) {
            this.reader = reader;
            this.keys = reader.keys();
        }

        @Override
        public boolean hasNext() {
            return this.index < this.keys.size();
        }

        @Override
        public ObjectReader next() {
            return this.reader.get(this.keys.get(this.index ++));
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("not supported yet");
        }
    }
}