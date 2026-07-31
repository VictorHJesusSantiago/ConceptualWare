package com.conceptualware.core.os;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;
import java.util.stream.*;

public class FileSystemOps {

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
        Object   fileKey
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

    public static InodeInfo stat(Path path) throws IOException {
        BasicFileAttributes basic = Files.readAttributes(path, BasicFileAttributes.class);
        String owner = "unknown";
        Set<PosixFilePermission> perms = new HashSet<>();

        try {
            PosixFileAttributes posix = Files.readAttributes(path, PosixFileAttributes.class);
            owner = posix.owner().getName();
            perms = posix.permissions();
        } catch (UnsupportedOperationException ignored) {
            perms = new HashSet<>();
        }

        return new InodeInfo(
            path, basic.size(), basic.isDirectory(), basic.isSymbolicLink(),
            basic.creationTime(), basic.lastModifiedTime(), basic.lastAccessTime(),
            owner, perms, basic.fileKey()
        );
    }

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

    public static List<Path> walkDirectory(Path root, int maxDepth) throws IOException {
        try (Stream<Path> stream = Files.walk(root, maxDepth)) {
            return stream.collect(Collectors.toList());
        }
    }

    public static Path safeResolveFile(Path baseDir, String userSuppliedRelativePath) {
        Path base = baseDir.toAbsolutePath().normalize();
        Path resolved = base.resolve(userSuppliedRelativePath).normalize();

        if (!resolved.startsWith(base)) {
            throw new SecurityException(
                "Path traversal detectado: '" + userSuppliedRelativePath + "' escapa do diretório base " + base);
        }
        return resolved;
    }

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
                return FileVisitResult.CONTINUE;
            }
        });
        return sizes;
    }

    private static String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(dot + 1).toLowerCase() : "(no ext)";
    }

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

    public static void withExclusiveLock(Path path, Runnable action) throws IOException {
        try (var channel = java.nio.channels.FileChannel.open(path,
                 StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = channel.lock()) {
            action.run();
        }
    }

    public static Map<String, String> readProcStatus() {
        Map<String, String> info = new LinkedHashMap<>();
        try {
            Files.lines(Path.of("/proc/self/status")).forEach(line -> {
                String[] parts = line.split(":", 2);
                if (parts.length == 2) info.put(parts[0].trim(), parts[1].trim());
            });
        } catch (IOException ignored) {
            info.put("VmRSS", "N/A (not Linux)");
            info.put("VmSize", "N/A (not Linux)");
        }
        return info;
    }

    public record SimulatedInode(
        int    inodeNumber,
        String name,
        long   size,
        int    linkCount,
        int[]  directBlocks,
        int    singleIndirect,
        int    doubleIndirect
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
