package com.conceptualware.core.algorithms;

import java.util.*;
import java.io.*;
import java.util.zip.*;

/**
 * Concept #5 — Compression Algorithms (Algoritmos de Compressão):
 *
 *   LZ77 — sliding window dictionary compression (1977, Lempel-Ziv)
 *     Uses a sliding window as "dictionary" — encodes matches as (offset, length, next_char) triples.
 *     Foundation for: gzip, PNG, ZIP, zlib, DEFLATE.
 *
 *   LZW — dictionary-based compression (1984, Lempel-Ziv-Welch)
 *     Builds dictionary dynamically during encoding, no need to transmit dict.
 *     Used by: GIF, TIFF, old modem compression (V.42bis).
 *
 *   DEFLATE — LZ77 + Huffman coding (RFC 1951)
 *     Java's java.util.zip uses DEFLATE internally.
 *     Used by: gzip, PNG, ZIP, HTTP Content-Encoding: deflate.
 *
 *   Huffman Coding — optimal prefix-free entropy encoding.
 *     Used in: JPEG, MP3, DEFLATE second pass.
 *
 * Concept #5 — Greedy algorithms (Huffman), sliding window pattern
 */
public class CompressionAlgorithms {

    // ── LZ77 ──────────────────────────────────────────────────────────────────

    /** LZ77 token: either a back-reference or a literal. */
    public sealed interface LZ77Token {
        record Literal(char ch) implements LZ77Token {}
        record Match(int offset, int length, char nextChar) implements LZ77Token {}
    }

    /**
     * LZ77 compression with sliding window.
     * @param input        string to compress
     * @param windowSize   search buffer size (lookback distance)
     * @param lookaheadSize max match length
     */
    public static List<LZ77Token> lz77Compress(String input, int windowSize, int lookaheadSize) {
        List<LZ77Token> tokens = new ArrayList<>();
        int pos = 0;

        while (pos < input.length()) {
            int bestOffset = 0, bestLength = 0;
            int searchStart = Math.max(0, pos - windowSize);

            // Find longest match in search buffer
            for (int i = searchStart; i < pos; i++) {
                int len = 0;
                while (len < lookaheadSize
                       && pos + len < input.length()
                       && input.charAt(i + len) == input.charAt(pos + len)) {
                    len++;
                }
                if (len > bestLength) {
                    bestLength = len;
                    bestOffset = pos - i;
                }
            }

            if (bestLength >= 2) {
                char next = (pos + bestLength < input.length()) ? input.charAt(pos + bestLength) : '\0';
                tokens.add(new LZ77Token.Match(bestOffset, bestLength, next));
                pos += bestLength + 1;
            } else {
                tokens.add(new LZ77Token.Literal(input.charAt(pos)));
                pos++;
            }
        }
        return tokens;
    }

    public static String lz77Decompress(List<LZ77Token> tokens) {
        StringBuilder sb = new StringBuilder();
        for (LZ77Token token : tokens) {
            switch (token) {
                case LZ77Token.Literal(char ch) -> sb.append(ch);
                case LZ77Token.Match(int offset, int length, char next) -> {
                    int start = sb.length() - offset;
                    for (int i = 0; i < length; i++)
                        sb.append(sb.charAt(start + i));
                    if (next != '\0') sb.append(next);
                }
            }
        }
        return sb.toString();
    }

    // ── LZW ───────────────────────────────────────────────────────────────────

    /**
     * LZW compression: builds dictionary from input, outputs code indices.
     * Initial dictionary: all single ASCII characters (256 entries).
     */
    public static List<Integer> lzwCompress(String input) {
        Map<String, Integer> dict = new HashMap<>();
        for (int i = 0; i < 256; i++) dict.put(String.valueOf((char) i), i);

        List<Integer> output = new ArrayList<>();
        String w = "";

        for (char c : input.toCharArray()) {
            String wc = w + c;
            if (dict.containsKey(wc)) {
                w = wc;
            } else {
                output.add(dict.get(w));
                dict.put(wc, dict.size());
                w = String.valueOf(c);
            }
        }
        if (!w.isEmpty()) output.add(dict.get(w));
        return output;
    }

    public static String lzwDecompress(List<Integer> codes) {
        Map<Integer, String> dict = new HashMap<>();
        for (int i = 0; i < 256; i++) dict.put(i, String.valueOf((char) i));

        StringBuilder result = new StringBuilder();
        String entry, prev = dict.get(codes.get(0));
        result.append(prev);

        for (int i = 1; i < codes.size(); i++) {
            int code = codes.get(i);
            if (dict.containsKey(code)) {
                entry = dict.get(code);
            } else if (code == dict.size()) {
                entry = prev + prev.charAt(0); // special case
            } else {
                throw new IllegalArgumentException("Invalid LZW code: " + code);
            }
            result.append(entry);
            dict.put(dict.size(), prev + entry.charAt(0));
            prev = entry;
        }
        return result.toString();
    }

    // ── DEFLATE (via java.util.zip) ───────────────────────────────────────────

    /**
     * DEFLATE compression using Java's built-in Deflater (RFC 1951).
     * Internally: LZ77 back-references + Huffman coding of (literal, length, distance) symbols.
     */
    public static byte[] deflateCompress(byte[] input) throws IOException {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
        deflater.setInput(input);
        deflater.finish();

        ByteArrayOutputStream baos = new ByteArrayOutputStream(input.length);
        byte[] buffer = new byte[1024];
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            baos.write(buffer, 0, count);
        }
        deflater.end();
        return baos.toByteArray();
    }

    public static byte[] deflateDecompress(byte[] compressed) throws IOException {
        Inflater inflater = new Inflater(true);
        inflater.setInput(compressed);

        ByteArrayOutputStream baos = new ByteArrayOutputStream(compressed.length * 3);
        byte[] buffer = new byte[1024];
        try {
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                baos.write(buffer, 0, count);
            }
        } catch (DataFormatException e) {
            throw new IOException("Invalid DEFLATE data", e);
        } finally {
            inflater.end();
        }
        return baos.toByteArray();
    }

    // ── Huffman Coding ────────────────────────────────────────────────────────

    /** Huffman tree node. */
    private static class HuffmanNode implements Comparable<HuffmanNode> {
        char ch;
        int  freq;
        HuffmanNode left, right;

        HuffmanNode(char ch, int freq) { this.ch = ch; this.freq = freq; }
        HuffmanNode(HuffmanNode l, HuffmanNode r) {
            this.freq = l.freq + r.freq;
            this.left = l; this.right = r;
        }

        boolean isLeaf() { return left == null && right == null; }

        @Override public int compareTo(HuffmanNode o) { return Integer.compare(freq, o.freq); }
    }

    /** Build Huffman tree and return the code table (char → bit string). */
    public static Map<Character, String> buildHuffmanCodes(String input) {
        Map<Character, Integer> freq = new HashMap<>();
        for (char c : input.toCharArray()) freq.merge(c, 1, Integer::sum);

        PriorityQueue<HuffmanNode> pq = new PriorityQueue<>();
        freq.forEach((c, f) -> pq.add(new HuffmanNode(c, f)));

        // Single character special case
        if (pq.size() == 1) {
            Map<Character, String> codes = new HashMap<>();
            codes.put(pq.peek().ch, "0");
            return codes;
        }

        while (pq.size() > 1) {
            pq.add(new HuffmanNode(pq.poll(), pq.poll()));
        }

        Map<Character, String> codes = new HashMap<>();
        buildCodes(pq.poll(), "", codes);
        return codes;
    }

    private static void buildCodes(HuffmanNode node, String code, Map<Character, String> codes) {
        if (node == null) return;
        if (node.isLeaf()) { codes.put(node.ch, code); return; }
        buildCodes(node.left,  code + "0", codes);
        buildCodes(node.right, code + "1", codes);
    }

    public static String huffmanEncode(String input, Map<Character, String> codes) {
        StringBuilder sb = new StringBuilder();
        for (char c : input.toCharArray()) sb.append(codes.get(c));
        return sb.toString();
    }

    // ── Run-Length Encoding (RLE) ─────────────────────────────────────────────

    public static String rleEncode(String input) {
        if (input.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int count = 1;
        for (int i = 1; i < input.length(); i++) {
            if (input.charAt(i) == input.charAt(i - 1)) {
                count++;
            } else {
                sb.append(input.charAt(i - 1));
                if (count > 1) sb.append(count);
                count = 1;
            }
        }
        sb.append(input.charAt(input.length() - 1));
        if (count > 1) sb.append(count);
        return sb.toString();
    }

    // ── Compression ratio analysis ─────────────────────────────────────────────

    public record CompressionResult(String algorithm, int originalBytes, int compressedSize, double ratio) {
        public double savings() { return (1.0 - ratio) * 100; }

        @Override public String toString() {
            return "%s: %d→%d bytes (%.1f%% savings)".formatted(algorithm, originalBytes, compressedSize, savings());
        }
    }
}
