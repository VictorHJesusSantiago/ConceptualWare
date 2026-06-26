package com.conceptualware.core.datastructures.hash;

import java.util.*;

/**
 * Concept #4 — Tabela hash, Conjunto (Set), Dicionário (Map), Multiset
 * Concept #5 — Algoritmos de hashing, collision resolution
 * Concept #28 — Propriedades matemáticas de funções hash
 */
public class HashTable<K, V> {

    private static final int DEFAULT_CAPACITY = 16;
    private static final float LOAD_FACTOR = 0.75f;

    private static class Entry<K, V> {
        final K key;
        V value;
        Entry<K, V> next;
        Entry(K k, V v) { key = k; value = v; }
    }

    private Entry<K, V>[] buckets;
    private int size;
    private int capacity;

    @SuppressWarnings("unchecked")
    public HashTable() {
        this.capacity = DEFAULT_CAPACITY;
        this.buckets = new Entry[capacity];
    }

    // ── Hash Function ─────────────────────────────────────────────────────────

    private int hash(K key) {
        if (key == null) return 0;
        int h = key.hashCode();
        // Fibonacci hashing — distributes keys more uniformly (Concept #5)
        h ^= (h >>> 16);
        return Math.abs(h % capacity);
    }

    // ── CRUD Operations ───────────────────────────────────────────────────────

    public void put(K key, V value) {
        if ((float) size / capacity >= LOAD_FACTOR) resize();
        int idx = hash(key);
        Entry<K, V> curr = buckets[idx];
        while (curr != null) {
            if (Objects.equals(curr.key, key)) { curr.value = value; return; }
            curr = curr.next;
        }
        Entry<K, V> entry = new Entry<>(key, value);
        entry.next = buckets[idx];
        buckets[idx] = entry;
        size++;
    }

    public V get(K key) {
        Entry<K, V> entry = findEntry(key);
        return entry != null ? entry.value : null;
    }

    public boolean containsKey(K key) { return findEntry(key) != null; }

    public V remove(K key) {
        int idx = hash(key);
        Entry<K, V> curr = buckets[idx], prev = null;
        while (curr != null) {
            if (Objects.equals(curr.key, key)) {
                if (prev == null) buckets[idx] = curr.next;
                else prev.next = curr.next;
                size--;
                return curr.value;
            }
            prev = curr;
            curr = curr.next;
        }
        return null;
    }

    private Entry<K, V> findEntry(K key) {
        Entry<K, V> curr = buckets[hash(key)];
        while (curr != null) {
            if (Objects.equals(curr.key, key)) return curr;
            curr = curr.next;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private void resize() {
        capacity *= 2;
        Entry<K, V>[] newBuckets = new Entry[capacity];
        for (Entry<K, V> head : buckets) {
            Entry<K, V> curr = head;
            while (curr != null) {
                Entry<K, V> next = curr.next;
                int idx = Math.abs(curr.key.hashCode() % capacity);
                curr.next = newBuckets[idx];
                newBuckets[idx] = curr;
                curr = next;
            }
        }
        buckets = newBuckets;
    }

    public int size() { return size; }
    public boolean isEmpty() { return size == 0; }

    public Set<K> keySet() {
        Set<K> keys = new HashSet<>();
        for (Entry<K, V> bucket : buckets) {
            Entry<K, V> curr = bucket;
            while (curr != null) { keys.add(curr.key); curr = curr.next; }
        }
        return keys;
    }

    // ── Bloom Filter ───────────────────────────────────────────────────────────

    public static class BloomFilter {
        private final long[] bitset;
        private final int size;
        private final int hashFunctions;

        public BloomFilter(int expectedInsertions, double falsePositiveRate) {
            this.size = optimalSize(expectedInsertions, falsePositiveRate);
            this.hashFunctions = optimalHashFunctions(expectedInsertions, this.size);
            this.bitset = new long[(size + 63) / 64];
        }

        public void add(String item) {
            for (int i = 0; i < hashFunctions; i++) {
                setBit(hash(item, i));
            }
        }

        /** Returns false = definitely NOT in set; true = POSSIBLY in set. */
        public boolean mightContain(String item) {
            for (int i = 0; i < hashFunctions; i++) {
                if (!isBitSet(hash(item, i))) return false;
            }
            return true;
        }

        private int hash(String item, int seed) {
            int h = item.hashCode() ^ (seed * 0x9e3779b9);
            h ^= h >>> 16;
            return Math.abs(h % size);
        }

        private void setBit(int idx) { bitset[idx / 64] |= (1L << (idx % 64)); }
        private boolean isBitSet(int idx) { return (bitset[idx / 64] & (1L << (idx % 64))) != 0; }

        private static int optimalSize(int n, double p) {
            return (int) (-n * Math.log(p) / (Math.log(2) * Math.log(2)));
        }

        private static int optimalHashFunctions(int n, int m) {
            return Math.max(1, (int) Math.round((double) m / n * Math.log(2)));
        }
    }

    // ── LRU Cache ────────────────────────────────────────────────────────────

    public static class LRUCache<K, V> {
        private final int capacity;
        private final LinkedHashMap<K, V> cache;

        public LRUCache(int capacity) {
            this.capacity = capacity;
            this.cache = new LinkedHashMap<>(capacity, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                    return size() > capacity;
                }
            };
        }

        public V get(K key) { return cache.getOrDefault(key, null); }
        public void put(K key, V value) { cache.put(key, value); }
        public boolean containsKey(K key) { return cache.containsKey(key); }
        public int size() { return cache.size(); }
    }
}
