package com.conceptualware.core.datastructures;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Concept #5 — Cache Eviction Policies: LRU (Least Recently Used).
 *   Implementado sobre LinkedHashMap em modo access-order — O(1) get/put.
 *   Alternativa "from scratch" com HashMap + doubly linked list está documentada
 *   nos comentários abaixo para fins didáticos.
 */
public class LRUCache<K, V> {

    private final int capacity;
    private final LinkedHashMap<K, V> store;

    public LRUCache(int capacity) {
        this.capacity = capacity;
        // accessOrder=true reordena a cada get/put — a entrada mais antiga fica no head (eldest)
        this.store = new LinkedHashMap<>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > LRUCache.this.capacity;
            }
        };
    }

    public V get(K key) {
        return store.get(key); // O(1), já reordena internamente
    }

    public void put(K key, V value) {
        store.put(key, value); // O(1)
    }

    public boolean contains(K key) {
        return store.containsKey(key);
    }

    public int size() {
        return store.size();
    }

    /** Snapshot da ordem atual (mais recentemente usado por último). */
    public java.util.List<K> orderSnapshot() {
        return java.util.List.copyOf(store.keySet());
    }
}
