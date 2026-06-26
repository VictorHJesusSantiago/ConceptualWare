package com.conceptualware.core.datastructures.tree;

import java.util.*;

/**
 * Concept #4 — Trie (Prefix Tree / Radix Tree)
 *   - Insert, search, startsWith in O(L) where L = word length
 *   - Auto-complete, word frequency, longest common prefix
 * Concept #5 — String searching, prefix matching
 */
public class Trie {

    private static class TrieNode {
        Map<Character, TrieNode> children = new HashMap<>();
        boolean isEndOfWord;
        int wordCount; // frequency of complete words ending here
        int prefixCount; // number of words passing through this node
    }

    private final TrieNode root = new TrieNode();
    private int totalWords;

    public void insert(String word) {
        TrieNode curr = root;
        for (char c : word.toCharArray()) {
            curr.children.putIfAbsent(c, new TrieNode());
            curr = curr.children.get(c);
            curr.prefixCount++;
        }
        curr.isEndOfWord = true;
        curr.wordCount++;
        totalWords++;
    }

    public boolean search(String word) {
        TrieNode node = findNode(word);
        return node != null && node.isEndOfWord;
    }

    public boolean startsWith(String prefix) {
        return findNode(prefix) != null;
    }

    public int countWordsWithPrefix(String prefix) {
        TrieNode node = findNode(prefix);
        return node == null ? 0 : node.prefixCount;
    }

    private TrieNode findNode(String prefix) {
        TrieNode curr = root;
        for (char c : prefix.toCharArray()) {
            if (!curr.children.containsKey(c)) return null;
            curr = curr.children.get(c);
        }
        return curr;
    }

    /** Auto-complete: return all words with the given prefix. */
    public List<String> autocomplete(String prefix) {
        List<String> results = new ArrayList<>();
        TrieNode node = findNode(prefix);
        if (node != null) dfs(node, new StringBuilder(prefix), results);
        return results;
    }

    private void dfs(TrieNode node, StringBuilder current, List<String> results) {
        if (node.isEndOfWord) results.add(current.toString());
        for (Map.Entry<Character, TrieNode> entry : node.children.entrySet()) {
            current.append(entry.getKey());
            dfs(entry.getValue(), current, results);
            current.deleteCharAt(current.length() - 1);
        }
    }

    /** Delete a word from the trie. */
    public boolean delete(String word) {
        if (!search(word)) return false;
        delete(root, word, 0);
        totalWords--;
        return true;
    }

    private boolean delete(TrieNode curr, String word, int depth) {
        if (depth == word.length()) {
            curr.isEndOfWord = false;
            curr.wordCount--;
            return curr.children.isEmpty();
        }
        char c = word.charAt(depth);
        TrieNode next = curr.children.get(c);
        if (next == null) return false;
        next.prefixCount--;
        boolean shouldDelete = delete(next, word, depth + 1);
        if (shouldDelete) curr.children.remove(c);
        return curr.children.isEmpty() && !curr.isEndOfWord;
    }

    /** Longest common prefix of all words in trie. */
    public String longestCommonPrefix() {
        StringBuilder lcp = new StringBuilder();
        TrieNode curr = root;
        while (curr.children.size() == 1 && !curr.isEndOfWord) {
            Map.Entry<Character, TrieNode> entry = curr.children.entrySet().iterator().next();
            lcp.append(entry.getKey());
            curr = entry.getValue();
        }
        return lcp.toString();
    }

    public int size() { return totalWords; }
}
