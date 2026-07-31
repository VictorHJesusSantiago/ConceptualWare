package com.conceptualware.core.datastructures;

import java.util.*;

public class LFUCache<K, V> {

    private final int capacity;
    private int minFreq;
    private final Map<K, V> values = new HashMap<>();
    private final Map<K, Integer> freqs = new HashMap<>();
    private final Map<Integer, LinkedHashSet<K>> freqBuckets = new HashMap<>();

    public LFUCache(int capacity) {
        this.capacity = capacity;
    }

    public V get(K key) {
        if (!values.containsKey(key)) return null;
        touch(key);
        return values.get(key);
    }

    public void put(K key, V value) {
        if (capacity <= 0) return;

        if (values.containsKey(key)) {
            values.put(key, value);
            touch(key);
            return;
        }

        if (values.size() >= capacity) {
            evict();
        }

        values.put(key, value);
        freqs.put(key, 1);
        freqBuckets.computeIfAbsent(1, f -> new LinkedHashSet<>()).add(key);
        minFreq = 1;
    }

    private void touch(K key) {
        int freq = freqs.get(key);
        freqBuckets.get(freq).remove(key);
        if (freqBuckets.get(freq).isEmpty()) {
            freqBuckets.remove(freq);
            if (minFreq == freq) minFreq++;
        }
        int newFreq = freq + 1;
        freqs.put(key, newFreq);
        freqBuckets.computeIfAbsent(newFreq, f -> new LinkedHashSet<>()).add(key);
    }

    private void evict() {
        LinkedHashSet<K> bucket = freqBuckets.get(minFreq);
        K evictKey = bucket.iterator().next();
        bucket.remove(evictKey);
        if (bucket.isEmpty()) freqBuckets.remove(minFreq);
        values.remove(evictKey);
        freqs.remove(evictKey);
    }

    public int size() {
        return values.size();
    }

    public boolean contains(K key) {
        return values.containsKey(key);
    }
}
