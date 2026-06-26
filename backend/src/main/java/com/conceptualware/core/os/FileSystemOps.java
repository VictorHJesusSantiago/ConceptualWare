package com.conceptualware.core.os;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;
import java.util.stream.*;

/**
 * Concept #17 — File System Operations (Sistema de Arquivos):
 *
 *   Inodes: Unix/Linux data structure storing file metadata.
 *     Each file has one inode with: permissions, owner, timestamps, data block pointers.
 *     Directory entries map filenames → inode numbers (inodes don't store names).
 *     Java: BasicFileAttributes, PosixFileAttributes.
 *
 *   File permissions: Unix rwxrwxrwx (owner/group/other).
 *     Java NIO: PosixFilePermissions, Files.setPosixFilePermissions().
 *
 *   Directory tree traversal: Files.walk(), FileVisitor pattern.
 *
 *   Hard links vs Symbolic links:
 *     Hard link: additional directory entry pointing to same inode (same inode number).
 *     Symbolic link: file containing a path to another file (different inode).
 *
 *   Watch service: OS-level file system event notification (inotify on Linux, FSEvents on macOS).
 *
 *   Memory-mapped files: mmap() — file I/O via memory addresses.
 *     Java: FileChannel.map() → MappedByteBuffer.
 *
 * Concept #17 — OS, filesystem, POSIX API
 */
public class FileSystemOps {

    // ── Inode-equivalent metadata ─────────────────────────────────────────────

    public record InodeInfo(
        Path     path,
        long     size,
        boolean  isDirectory,
        boolean  isSymbolicLink,
        FileTime creationTime,
        FileTime lastModified,
        FileTime lastAccessed,
        String   owner,
        Set<PosixFilePermission> permissions,
        Object   fileKey  // inode number equivalent on this OS
    ) {
        public String permissionsString() {
            return PosixFilePermissions.toString(permissions);
        }

        @Override public String toString() {
            return "[%s] %s %s %d bytes (modified %s)".formatted(
                permissionsString(), isDirectory ? "d" : "-", path.getFileName(),
                size, lastModified
            );
        }
    }

    /** Read inode-equivalent metadata from a file (Java NIO equivalent of stat()). */
    public static InodeInfo stat(Path path) throws IOException {
        BasicFileAttributes basic = Files.readAttributes(path, BasicFileAttributes.class);
        String owner = "unknown";
        Set<PosixFilePermission> perms = new HashSet<>();

        // POSIX attributes only available on Unix/Linux/macOS
        try {
            PosixFileAttributes posix = Files.readAttributes(path, PosixFileAttributes.class);
            owner = posix.owner().getName();
            perms = posix.permissions();
        } catch (UnsupportedOperationException ignored) {
            // Windows — no POSIX attributes
            perms = new HashSet<>();
        }

        return new InodeInfo(
            path, basic.size(), basic.isDirectory(), basic.isSymbolicLink(),
            basic.creationTime(), basic.lastModifiedTime(), basic.lastAccessTime(),
            owner, perms, basic.fileKey()
        );
    }

    // ── File permissions ──────────────────────────────────────────────────────

    /** Set permissions using Unix octal notation (e.g., 0755 → rwxr-xr-x). */
    public static void setPermissions(Path path, String posixPerms) throws IOException {
        Set<PosixFilePermission> perms = PosixFilePermissions.fromString(posixPerms);
        Files.setPosixFilePermissions(path, perms);
    }

    public static Set<PosixFilePermission> octalToPermissions(int octal) {
        Set<PosixFilePermission> perms = new HashSet<>();
        if ((octal & 0400) != 0) perms.add(PosixFilePermission.OWNER_READ);
        if ((octal & 0200) != 0) perms.add(PosixFilePermission.OWNER_WRITE);
        if ((octal & 0100) != 0) perms.add(PosixFilePermission.OWNER_EXECUTE);
        if ((octal & 0040) != 0) perms.add(PosixFilePermission.GROUP_READ);
        if ((octal & 0020) != 0) perms.add(PosixFilePermission.GROUP_WRITE);
        if ((octal & 0010) != 0) perms.add(PosixFilePermission.GROUP_EXECUTE);
        if ((octal & 0004) != 0) perms.add(PosixFilePermission.OTHERS_READ);
        if ((octal & 0002) != 0) perms.add(PosixFilePermission.OTHERS_WRITE);
        if ((octal & 0001) != 0) perms.add(PosixFilePermission.OTHERS_EXECUTE);
        return perms;
    }

    // ── Directory traversal ───────────────────────────────────────────────────

    /** Recursive directory walk (like `find .`). */
    public static List<Path> walkDirectory(Path root, int maxDepth) throws IOException {
        try (Stream<Path> stream = Files.walk(root, maxDepth)) {
            return stream.collect(Collectors.toList());
        }
    }

    /** FileVisitor pattern — full control over traversal (preVisit, postVisit, error). */
    public static Map<String, Long> sizeByExtension(Path root) throws IOException {
        Map<String, Long> sizes = new TreeMap<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String ext = getExtension(file.getFileName().toString());
                sizes.merge(ext, attrs.size(), Long::sum);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE; // skip unreadable files
            }
        });
        return sizes;
    }

    private static String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(dot + 1).toLowerCase() : "(no ext)";
    }

    // ── Links ─────────────────────────────────────────────────────────────────

    public static void createSymlink(Path link, Path target) throws IOException {
        Files.createSymbolicLink(link, target);
    }

    public static void createHardLink(Path link, Path existing) throws IOException {
        Files.createLink(link, existing);
    }

    public static boolean isSameInode(Path a, Path b) throws IOException {
        Object keyA = Files.readAttributes(a, BasicFileAttributes.class).fileKey();
        Object keyB = Files.readAttributes(b, BasicFileAttributes.class).fileKey();
        return keyA != null && keyA.equals(keyB);
    }

    // ── Atomic file operations ────────────────────────────────────────────────

    /**
     * Atomic write: write to temp file then rename.
     * On Linux/POSIX, rename() is atomic — readers see either old or new content, never partial.
     */
    public static void atomicWrite(Path target, byte[] content) throws IOException {
        Path temp = target.resolveSibling(target.getFileName() + ".tmp." + System.nanoTime());
        try {
            Files.write(temp, content);
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            Files.deleteIfExists(temp);
            throw e;
        }
    }

    // ── Memory-mapped file I/O ────────────────────────────────────────────────

    /** Memory-map a file for zero-copy I/O — efficient for large files. */
    public static byte[] readMemoryMapped(Path path) throws IOException {
        try (var channel = java.nio.channels.FileChannel.open(path, StandardOpenOption.READ)) {
            long size = channel.size();
            if (size == 0) return new byte[0];

            java.nio.MappedByteBuffer buf = channel.map(
                java.nio.channels.FileChannel.MapMode.READ_ONLY, 0, size);
            byte[] result = new byte[(int) size];
            buf.get(result);
            return result;
        }
    }

    // ── File locking ──────────────────────────────────────────────────────────

    /**
     * Exclusive file lock (advisory on most Unix systems — equivalent of flock()).
     * Prevents concurrent writes from multiple processes.
     */
    public static void withExclusiveLock(Path path, Runnable action) throws IOException {
        try (var channel = java.nio.channels.FileChannel.open(path,
                 StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = channel.lock()) { // blocks until lock acquired
            action.run();
        }
    }

    // ── /proc virtual filesystem concepts (read-only on Linux) ───────────────

    /** Read /proc/self/status to get process memory info (Linux only). */
    public static Map<String, String> readProcStatus() {
        Map<String, String> info = new LinkedHashMap<>();
        try {
            Files.lines(Path.of("/proc/self/status")).forEach(line -> {
                String[] parts = line.split(":", 2);
                if (parts.length == 2) info.put(parts[0].trim(), parts[1].trim());
            });
        } catch (IOException ignored) {
            // Not on Linux or /proc not available
            info.put("VmRSS", "N/A (not Linux)");
            info.put("VmSize", "N/A (not Linux)");
        }
        return info;
    }

    /** Inode simulation: tracks files and their block allocations. */
    public record SimulatedInode(
        int    inodeNumber,
        String name,
        long   size,
        int    linkCount,
        int[]  directBlocks,   // first 12 data blocks
        int    singleIndirect, // points to block containing block addresses
        int    doubleIndirect  // two levels of indirection for very large files
    ) {
        public static SimulatedInode create(int num, String name, long sizeBytes) {
            int blockSize = 4096;
            int blocks    = (int) Math.ceil((double) sizeBytes / blockSize);
            int direct    = Math.min(blocks, 12);
            int[] directBlocks = new int[direct];
            for (int i = 0; i < direct; i++) directBlocks[i] = num * 100 + i;
            return new SimulatedInode(num, name, sizeBytes, 1, directBlocks,
                blocks > 12 ? num * 100 + 12 : -1,
                blocks > 12 + 1024 ? num * 100 + 13 : -1);
        }
    }
}
