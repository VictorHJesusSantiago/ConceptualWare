package com.conceptualware.core.datastructures;

import java.util.LinkedHashMap;
import java.util.Map;

public class LRUCache<K, V> {

    private final int capacity;
    private final LinkedHashMap<K, V> store;

    public LRUCache(int capacity) {
        this.capacity = capacity;
        this.store = new LinkedHashMap<>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > LRUCache.this.capacity;
            }
        };
    }

    public V get(K key) {
        return store.get(key);
    }

    public void put(K key, V value) {
        store.put(key, value);
    }

    public boolean contains(K key) {
        return store.containsKey(key);
    }

    public int size() {
        return store.size();
    }

    public java.util.List<K> orderSnapshot() {
        return java.util.List.copyOf(store.keySet());
    }
}
