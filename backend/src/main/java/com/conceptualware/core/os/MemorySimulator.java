package com.conceptualware.core.os;

import java.util.*;

/**
 * Concept #17 — Memory Management (Gerenciamento de Memória):
 *
 *   Paging: physical memory divided into fixed-size frames, logical memory into pages.
 *     - Page table: maps virtual page number → physical frame number.
 *     - Address translation: virtual_address = (page_number * page_size) + offset.
 *     - TLB (Translation Lookaside Buffer): cache for page table entries.
 *     - Page fault: page not in memory → OS loads it from disk (demand paging).
 *
 *   Page Replacement Algorithms:
 *     - FIFO: evict the oldest loaded page (can have Belady's anomaly).
 *     - LRU: evict least recently used (optimal approximation, used by Linux).
 *     - Optimal (OPT): evict page used farthest in future (theoretical benchmark).
 *     - Clock (Second Chance): FIFO with reference bit (approximates LRU, cheaper).
 *
 *   Segmentation: logical memory divided into variable-size segments (code, stack, heap).
 *     - Each segment has base + limit; unlike paging, preserves logical structure.
 *     - Can be combined with paging (Intel x86 segmentation + paging).
 *
 *   Memory Allocation:
 *     - First Fit, Best Fit, Worst Fit strategies for dynamic allocation.
 *     - Buddy System: splits/merges blocks in powers of 2 (used by Linux kernel).
 *
 * Concept #17 — OS concepts, virtual memory, address spaces
 */
public class MemorySimulator {

    // ── Paging ────────────────────────────────────────────────────────────────

    public static class PageTable {
        private final int pageSize;
        private final Map<Integer, Integer> entries = new LinkedHashMap<>(); // page → frame
        private final Set<Integer> presentBits      = new HashSet<>();
        private int frameCount = 0;
        private long pageFaults = 0, tlbHits = 0, tlbMisses = 0;
        private final Map<Integer, Integer> tlb = new LinkedHashMap<>(16, 0.75f, true) {
            // LRU TLB with max 16 entries
            protected boolean removeEldestEntry(Map.Entry<Integer, Integer> e) { return size() > 16; }
        };

        public PageTable(int pageSize) { this.pageSize = pageSize; }

        /** Translate virtual address to physical address. */
        public int translate(int virtualAddress) {
            int pageNum = virtualAddress / pageSize;
            int offset  = virtualAddress % pageSize;

            // TLB lookup
            Integer frame = tlb.get(pageNum);
            if (frame != null) {
                tlbHits++;
                return frame * pageSize + offset;
            }

            tlbMisses++;

            // Page table lookup
            if (!presentBits.contains(pageNum)) {
                pageFaults++;
                loadPage(pageNum);
            }

            frame = entries.get(pageNum);
            tlb.put(pageNum, frame);
            return frame * pageSize + offset;
        }

        private void loadPage(int pageNum) {
            entries.put(pageNum, frameCount++);
            presentBits.add(pageNum);
        }

        public long getPageFaults() { return pageFaults; }
        public long getTLBHits()    { return tlbHits; }
        public long getTLBMisses()  { return tlbMisses; }
        public double getTLBHitRate() {
            long total = tlbHits + tlbMisses;
            return total == 0 ? 0 : (double) tlbHits / total;
        }
    }

    // ── Page Replacement Algorithms ───────────────────────────────────────────

    /** Simulate a page replacement algorithm and return number of page faults. */
    public static int fifoPageFaults(int[] pages, int frames) {
        Set<Integer> inMemory = new LinkedHashSet<>();
        Queue<Integer> queue  = new LinkedList<>();
        int faults = 0;

        for (int page : pages) {
            if (!inMemory.contains(page)) {
                faults++;
                if (inMemory.size() == frames) {
                    int evict = queue.poll();
                    inMemory.remove(evict);
                }
                inMemory.add(page);
                queue.add(page);
            }
        }
        return faults;
    }

    public static int lruPageFaults(int[] pages, int frames) {
        // LinkedHashMap with access order = true → LRU ordering
        LinkedHashMap<Integer, Boolean> lruCache = new LinkedHashMap<>(16, 0.75f, true) {
            protected boolean removeEldestEntry(Map.Entry<Integer, Boolean> e) {
                return size() > frames;
            }
        };
        int faults = 0;

        for (int page : pages) {
            int sizeBefore = lruCache.size();
            lruCache.put(page, true);
            // Page fault if it wasn't present OR was evicted (size didn't increase past frames)
            if (sizeBefore < frames || !lruCache.containsKey(page)) {
                // Actually just check hits:
            }
        }

        // Cleaner LRU simulation:
        Set<Integer> inMemory = new LinkedHashSet<>();
        Deque<Integer> lruOrder = new ArrayDeque<>();
        faults = 0;

        for (int page : pages) {
            if (inMemory.contains(page)) {
                // Move to most recently used
                lruOrder.remove(page);
                lruOrder.addLast(page);
            } else {
                faults++;
                if (inMemory.size() == frames) {
                    int evict = lruOrder.pollFirst();
                    inMemory.remove(evict);
                }
                inMemory.add(page);
                lruOrder.addLast(page);
            }
        }
        return faults;
    }

    /** Optimal (OPT) page replacement — theoretical minimum page faults. */
    public static int optimalPageFaults(int[] pages, int frames) {
        Set<Integer> inMemory = new HashSet<>();
        int faults = 0;

        for (int i = 0; i < pages.length; i++) {
            if (!inMemory.contains(pages[i])) {
                faults++;
                if (inMemory.size() == frames) {
                    // Evict page used farthest in the future (or not used at all)
                    int evict = -1, farthest = i;
                    for (int page : inMemory) {
                        int nextUse = pages.length; // assume never used again
                        for (int j = i + 1; j < pages.length; j++) {
                            if (pages[j] == page) { nextUse = j; break; }
                        }
                        if (nextUse > farthest) {
                            farthest = nextUse;
                            evict    = page;
                        }
                    }
                    if (evict != -1) inMemory.remove(evict);
                }
                inMemory.add(pages[i]);
            }
        }
        return faults;
    }

    /** Clock algorithm (Second Chance) — O(1) per access, approximates LRU. */
    public static int clockPageFaults(int[] pages, int frames) {
        int[]     frameArr = new int[frames];
        boolean[] refBit   = new boolean[frames];
        Arrays.fill(frameArr, -1);
        int hand = 0, faults = 0;

        for (int page : pages) {
            boolean hit = false;
            for (int i = 0; i < frames; i++) {
                if (frameArr[i] == page) { refBit[i] = true; hit = true; break; }
            }
            if (hit) continue;

            faults++;
            while (refBit[hand]) {
                refBit[hand] = false;
                hand = (hand + 1) % frames;
            }
            frameArr[hand] = page;
            refBit[hand]   = true;
            hand = (hand + 1) % frames;
        }
        return faults;
    }

    // ── Segmentation ─────────────────────────────────────────────────────────

    public record Segment(String name, long base, long limit) {
        public boolean contains(long offset) { return offset >= 0 && offset < limit; }
        public long physicalAddress(long offset) {
            if (!contains(offset)) throw new SegmentationFaultException(name, offset, limit);
            return base + offset;
        }
    }

    public static class SegmentationFaultException extends RuntimeException {
        public SegmentationFaultException(String segment, long offset, long limit) {
            super("SIGSEGV: segment '%s' offset %d exceeds limit %d".formatted(segment, offset, limit));
        }
    }

    public static class SegmentTable {
        private final Map<String, Segment> segments = new LinkedHashMap<>();

        public void addSegment(String name, long base, long size) {
            segments.put(name, new Segment(name, base, size));
        }

        public long translate(String segmentName, long offset) {
            Segment seg = segments.get(segmentName);
            if (seg == null) throw new IllegalArgumentException("Unknown segment: " + segmentName);
            return seg.physicalAddress(offset);
        }

        public Map<String, Segment> getSegments() { return Collections.unmodifiableMap(segments); }
    }

    /** Create a typical process memory layout (Linux-like). */
    public static SegmentTable typicalProcessLayout() {
        SegmentTable st = new SegmentTable();
        st.addSegment("text",    0x400000L, 0x10000L);  // .text: code segment (read+execute)
        st.addSegment("data",    0x600000L, 0x1000L);   // .data: initialized globals (read+write)
        st.addSegment("bss",     0x601000L, 0x1000L);   // .bss: uninitialized globals (zero-filled)
        st.addSegment("heap",    0x602000L, 0x100000L); // heap: grows upward (malloc)
        st.addSegment("stack",   0x7fff0000L, 0x10000L);// stack: grows downward
        st.addSegment("mmap",    0x7f000000L, 0x100000L);// mmap: shared libs, anonymous mappings
        return st;
    }

    // ── Memory Allocation Strategies ─────────────────────────────────────────

    public record MemoryBlock(int start, int size, boolean free) {
        public MemoryBlock allocate() { return new MemoryBlock(start, size, false); }
    }

    public static class MemoryAllocator {
        private final List<MemoryBlock> blocks;

        public MemoryAllocator(int totalSize) {
            blocks = new ArrayList<>();
            blocks.add(new MemoryBlock(0, totalSize, true));
        }

        /** First Fit: allocate in first block large enough. O(n) scan. */
        public Optional<Integer> firstFit(int requestedSize) {
            for (int i = 0; i < blocks.size(); i++) {
                MemoryBlock b = blocks.get(i);
                if (b.free() && b.size() >= requestedSize) {
                    return Optional.of(split(i, requestedSize));
                }
            }
            return Optional.empty();
        }

        /** Best Fit: allocate in smallest free block that fits. Minimizes waste. */
        public Optional<Integer> bestFit(int requestedSize) {
            int bestIdx = -1, bestSize = Integer.MAX_VALUE;
            for (int i = 0; i < blocks.size(); i++) {
                MemoryBlock b = blocks.get(i);
                if (b.free() && b.size() >= requestedSize && b.size() < bestSize) {
                    bestIdx = i; bestSize = b.size();
                }
            }
            if (bestIdx == -1) return Optional.empty();
            return Optional.of(split(bestIdx, requestedSize));
        }

        /** Worst Fit: allocate in largest free block. Maximizes remaining fragment size. */
        public Optional<Integer> worstFit(int requestedSize) {
            int worstIdx = -1, worstSize = -1;
            for (int i = 0; i < blocks.size(); i++) {
                MemoryBlock b = blocks.get(i);
                if (b.free() && b.size() >= requestedSize && b.size() > worstSize) {
                    worstIdx = i; worstSize = b.size();
                }
            }
            if (worstIdx == -1) return Optional.empty();
            return Optional.of(split(worstIdx, requestedSize));
        }

        private int split(int idx, int size) {
            MemoryBlock b = blocks.get(idx);
            int start = b.start();
            if (b.size() > size) {
                // Split: allocated block + free remainder
                blocks.set(idx, new MemoryBlock(start, size, false));
                blocks.add(idx + 1, new MemoryBlock(start + size, b.size() - size, true));
            } else {
                blocks.set(idx, b.allocate());
            }
            return start;
        }

        public void free(int address) {
            for (int i = 0; i < blocks.size(); i++) {
                if (blocks.get(i).start() == address) {
                    blocks.set(i, new MemoryBlock(blocks.get(i).start(), blocks.get(i).size(), true));
                    coalesce(); // merge adjacent free blocks
                    return;
                }
            }
        }

        private void coalesce() {
            for (int i = 0; i < blocks.size() - 1; ) {
                MemoryBlock cur  = blocks.get(i);
                MemoryBlock next = blocks.get(i + 1);
                if (cur.free() && next.free()) {
                    blocks.set(i, new MemoryBlock(cur.start(), cur.size() + next.size(), true));
                    blocks.remove(i + 1);
                } else {
                    i++;
                }
            }
        }

        public List<MemoryBlock> getBlocks() { return Collections.unmodifiableList(blocks); }
        public int fragmentation() {
            return blocks.stream().filter(MemoryBlock::free).mapToInt(MemoryBlock::size).sum();
        }
    }
}
