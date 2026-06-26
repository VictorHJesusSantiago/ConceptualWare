package com.conceptualware.core.algorithms.string;

import java.util.*;

/**
 * Concept #5 — Algoritmos de String:
 *   KMP (Knuth-Morris-Pratt), Rabin-Karp, Boyer-Moore,
 *   Hashing, compressão Huffman, algoritmos de busca de padrão
 *
 * Concept #28 — Expressões regulares (base formal), Autômatos finitos
 * Concept #10 — Tokenizer / Lexer concepts
 */
public class StringAlgorithms {

    // ── KMP — O(n + m) pattern matching ──────────────────────────────────────

    public static List<Integer> kmpSearch(String text, String pattern) {
        List<Integer> matches = new ArrayList<>();
        int n = text.length(), m = pattern.length();
        if (m == 0 || m > n) return matches;

        int[] lps = computeLPS(pattern);
        int i = 0, j = 0;
        while (i < n) {
            if (text.charAt(i) == pattern.charAt(j)) { i++; j++; }
            if (j == m) { matches.add(i - j); j = lps[j - 1]; }
            else if (i < n && text.charAt(i) != pattern.charAt(j)) {
                if (j != 0) j = lps[j - 1]; else i++;
            }
        }
        return matches;
    }

    /** Longest Proper Prefix which is also Suffix (LPS / failure function). */
    private static int[] computeLPS(String pattern) {
        int m = pattern.length();
        int[] lps = new int[m];
        int len = 0, i = 1;
        while (i < m) {
            if (pattern.charAt(i) == pattern.charAt(len)) { lps[i++] = ++len; }
            else if (len != 0) { len = lps[len - 1]; }
            else { lps[i++] = 0; }
        }
        return lps;
    }

    // ── Rabin-Karp — O(n + m) avg, O(nm) worst ────────────────────────────────

    public static List<Integer> rabinKarpSearch(String text, String pattern) {
        List<Integer> matches = new ArrayList<>();
        int n = text.length(), m = pattern.length();
        if (m > n) return matches;

        final int BASE = 31, MOD = 1_000_000_007;
        long patternHash = 0, windowHash = 0, power = 1;

        for (int i = 0; i < m; i++) {
            patternHash = (patternHash * BASE + pattern.charAt(i)) % MOD;
            windowHash  = (windowHash  * BASE + text.charAt(i))    % MOD;
            if (i > 0) power = (power * BASE) % MOD;
        }

        for (int i = 0; i <= n - m; i++) {
            if (windowHash == patternHash && text.regionMatches(i, pattern, 0, m))
                matches.add(i);
            if (i < n - m)
                windowHash = (windowHash - text.charAt(i) * power % MOD + MOD) % MOD
                           * BASE % MOD + text.charAt(i + m);
        }
        return matches;
    }

    // ── Boyer-Moore (Bad Character heuristic) ─────────────────────────────────

    public static List<Integer> boyerMooreSearch(String text, String pattern) {
        List<Integer> matches = new ArrayList<>();
        int n = text.length(), m = pattern.length();
        int[] badChar = buildBadCharTable(pattern);

        int shift = 0;
        while (shift <= n - m) {
            int j = m - 1;
            while (j >= 0 && pattern.charAt(j) == text.charAt(shift + j)) j--;
            if (j < 0) {
                matches.add(shift);
                shift += (shift + m < n) ? m - badChar[text.charAt(shift + m)] : 1;
            } else {
                shift += Math.max(1, j - badChar[text.charAt(shift + j)]);
            }
        }
        return matches;
    }

    private static int[] buildBadCharTable(String pattern) {
        int[] table = new int[256];
        Arrays.fill(table, -1);
        for (int i = 0; i < pattern.length(); i++) table[pattern.charAt(i)] = i;
        return table;
    }

    // ── Huffman Coding — Concept #5 ──────────────────────────────────────────

    public static class HuffmanNode implements Comparable<HuffmanNode> {
        char ch;
        int freq;
        HuffmanNode left, right;
        HuffmanNode(char ch, int freq) { this.ch = ch; this.freq = freq; }
        HuffmanNode(HuffmanNode l, HuffmanNode r) {
            this.ch = '\0'; this.freq = l.freq + r.freq;
            this.left = l; this.right = r;
        }
        @Override
        public int compareTo(HuffmanNode o) { return this.freq - o.freq; }
        public boolean isLeaf() { return left == null && right == null; }
    }

    public static Map<Character, String> buildHuffmanCodes(String text) {
        Map<Character, Integer> freq = new HashMap<>();
        for (char c : text.toCharArray()) freq.merge(c, 1, Integer::sum);

        PriorityQueue<HuffmanNode> pq = new PriorityQueue<>();
        freq.forEach((ch, f) -> pq.offer(new HuffmanNode(ch, f)));

        while (pq.size() > 1) {
            HuffmanNode l = pq.poll(), r = pq.poll();
            pq.offer(new HuffmanNode(l, r));
        }
        Map<Character, String> codes = new HashMap<>();
        generateCodes(pq.poll(), "", codes);
        return codes;
    }

    private static void generateCodes(HuffmanNode node, String code, Map<Character, String> codes) {
        if (node == null) return;
        if (node.isLeaf()) { codes.put(node.ch, code.isEmpty() ? "0" : code); return; }
        generateCodes(node.left, code + "0", codes);
        generateCodes(node.right, code + "1", codes);
    }

    public static String huffmanEncode(String text, Map<Character, String> codes) {
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) sb.append(codes.get(c));
        return sb.toString();
    }

    // ── Anagram Detection ─────────────────────────────────────────────────────

    public static boolean isAnagram(String a, String b) {
        if (a.length() != b.length()) return false;
        int[] count = new int[26];
        for (int i = 0; i < a.length(); i++) {
            count[a.charAt(i) - 'a']++;
            count[b.charAt(i) - 'a']--;
        }
        for (int c : count) if (c != 0) return false;
        return true;
    }

    // ── Palindrome Check ───────────────────────────────────────────────────────

    public static boolean isPalindrome(String s) {
        int l = 0, r = s.length() - 1;
        while (l < r) {
            if (s.charAt(l++) != s.charAt(r--)) return false;
        }
        return true;
    }

    /** Longest Palindromic Substring — Manacher's algorithm O(n). */
    public static String longestPalindromicSubstring(String s) {
        String t = "#" + String.join("#", s.split("")) + "#";
        int n = t.length();
        int[] p = new int[n];
        int c = 0, r = 0, maxLen = 0, center = 0;
        for (int i = 0; i < n; i++) {
            int mirror = 2 * c - i;
            if (i < r) p[i] = Math.min(r - i, p[mirror]);
            while (i + p[i] + 1 < n && i - p[i] - 1 >= 0
                    && t.charAt(i + p[i] + 1) == t.charAt(i - p[i] - 1)) p[i]++;
            if (i + p[i] > r) { c = i; r = i + p[i]; }
            if (p[i] > maxLen) { maxLen = p[i]; center = i; }
        }
        int start = (center - maxLen) / 2;
        return s.substring(start, start + maxLen);
    }

    // ── String Hashing ─────────────────────────────────────────────────────────

    public static long polynomialHash(String s, long base, long mod) {
        long hash = 0, power = 1;
        for (char c : s.toCharArray()) {
            hash = (hash + c * power) % mod;
            power = (power * base) % mod;
        }
        return hash;
    }
}
