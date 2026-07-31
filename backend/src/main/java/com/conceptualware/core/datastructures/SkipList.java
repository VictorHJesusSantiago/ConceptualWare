package com.conceptualware.core.datastructures;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class SkipList<K extends Comparable<K>, V> {

    private static final double PROBABILITY = 0.5;
    private static final int    MAX_LEVEL   = 32;

    private final Node<K, V> head;
    private int level;
    private int size;

    @SuppressWarnings("unchecked")
    public SkipList() {
        head  = new Node<>(null, null, MAX_LEVEL);
        level = 0;
        size  = 0;
    }

    private static class Node<K, V> {
        K          key;
        V          value;
        Node<K, V>[] forward;

        @SuppressWarnings("unchecked")
        Node(K key, V value, int height) {
            this.key     = key;
            this.value   = value;
            this.forward = new Node[height];
        }
    }

    private int randomLevel() {
        int lvl = 1;
        while (lvl < MAX_LEVEL && ThreadLocalRandom.current().nextDouble() < PROBABILITY)
            lvl++;
        return lvl;
    }

    public Optional<V> get(K key) {
        Node<K, V> cur = head;
        for (int i = level - 1; i >= 0; i--) {
            while (cur.forward[i] != null && cur.forward[i].key.compareTo(key) < 0)
                cur = cur.forward[i];
        }
        cur = cur.forward[0];
        if (cur != null && cur.key.compareTo(key) == 0) return Optional.of(cur.value);
        return Optional.empty();
    }

    public boolean contains(K key) { return get(key).isPresent(); }

    @SuppressWarnings("unchecked")
    public void put(K key, V value) {
        Node<K, V>[] update = new Node[MAX_LEVEL];
        Node<K, V>   cur    = head;

        for (int i = level - 1; i >= 0; i--) {
            while (cur.forward[i] != null && cur.forward[i].key.compareTo(key) < 0)
                cur = cur.forward[i];
            update[i] = cur;
        }

        cur = cur.forward[0];

        if (cur != null && cur.key.compareTo(key) == 0) {
            cur.value = value;
            return;
        }

        int newLevel = randomLevel();
        if (newLevel > level) {
            for (int i = level; i < newLevel; i++) update[i] = head;
            level = newLevel;
        }

        Node<K, V> newNode = new Node<>(key, value, newLevel);
        for (int i = 0; i < newLevel; i++) {
            newNode.forward[i]  = update[i].forward[i];
            update[i].forward[i] = newNode;
        }
        size++;
    }

    @SuppressWarnings("unchecked")
    public boolean remove(K key) {
        Node<K, V>[] update = new Node[MAX_LEVEL];
        Node<K, V>   cur    = head;

        for (int i = level - 1; i >= 0; i--) {
            while (cur.forward[i] != null && cur.forward[i].key.compareTo(key) < 0)
                cur = cur.forward[i];
            update[i] = cur;
        }

        cur = cur.forward[0];

        if (cur == null || cur.key.compareTo(key) != 0) return false;

        for (int i = 0; i < level; i++) {
            if (update[i].forward[i] != cur) break;
            update[i].forward[i] = cur.forward[i];
        }

        while (level > 1 && head.forward[level - 1] == null) level--;

        size--;
        return true;
    }

    public List<Map.Entry<K, V>> range(K from, K to) {
        List<Map.Entry<K, V>> result = new ArrayList<>();
        Node<K, V> cur = head;

        for (int i = level - 1; i >= 0; i--) {
            while (cur.forward[i] != null && cur.forward[i].key.compareTo(from) < 0)
                cur = cur.forward[i];
        }
        cur = cur.forward[0];

        while (cur != null && cur.key.compareTo(to) <= 0) {
            result.add(Map.entry(cur.key, cur.value));
            cur = cur.forward[0];
        }
        return result;
    }

    public List<K> keys() {
        List<K> result = new ArrayList<>();
        Node<K, V> cur = head.forward[0];
        while (cur != null) { result.add(cur.key); cur = cur.forward[0]; }
        return result;
    }

    public int    size()    { return size; }
    public boolean isEmpty() { return size == 0; }
    public int    height()  { return level; }
}
