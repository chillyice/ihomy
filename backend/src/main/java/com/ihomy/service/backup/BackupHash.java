package com.ihomy.service.backup;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

/** 备份文件哈希/体积统计工具(流式,不整文件入堆)。 */
public final class BackupHash {

    private BackupHash() {
    }

    public static String sha256(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) digest.update(buf, 0, n);
        }
        return BackupSql.hex(digest.digest());
    }

    /** 目录内所有普通文件的逻辑字节合计(硬链接按文件大小重复计,只用于展示) */
    public static long directorySize(Path dir) throws IOException {
        if (dir == null || !Files.isDirectory(dir)) return 0;
        try (Stream<Path> stream = Files.walk(dir)) {
            return stream.filter(Files::isRegularFile).mapToLong(p -> {
                try {
                    return Files.size(p);
                } catch (IOException e) {
                    return 0L;
                }
            }).sum();
        }
    }

    /**
     * 目录实际占用(按 fileKey 去重硬链接):备份快照之间共享 inode,只统计一份。
     * Windows 上 fileKey 同样标识硬链接;拿不到 fileKey 时退回逻辑大小。
     */
    public static long diskUsage(Path dir) throws IOException {
        if (dir == null || !Files.isDirectory(dir)) return 0;
        Set<Object> seen = new HashSet<>();
        long sum = 0;
        try (Stream<Path> stream = Files.walk(dir)) {
            java.util.Iterator<Path> it = stream.filter(Files::isRegularFile).iterator();
            while (it.hasNext()) {
                try {
                    BasicFileAttributes attrs = Files.readAttributes(it.next(), BasicFileAttributes.class);
                    Object key = attrs.fileKey();
                    if (key != null && !seen.add(key)) continue;
                    sum += attrs.size();
                } catch (IOException ignored) {
                    // 单个文件读不到不影响整体统计
                }
            }
        }
        return sum;
    }
}
