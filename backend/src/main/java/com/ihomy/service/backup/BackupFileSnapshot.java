package com.ihomy.service.backup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * uploads 全量快照(硬链接增量)。
 *
 * <p>每一份内容备份都生成「当前 uploads 的完整视图」,但未变化的文件用硬链接指向上一次
 * 成功快照的同一份数据(mtime+size 相同即视为未变),只有新增/修改的文件才真正落盘。
 * 这样:①每份快照都完整可独立恢复;②磁盘只增长变化量;③备份目录被删除时,
 * 硬链接只释放自己那一份 inode,不会破坏其他快照。</p>
 *
 * <p>文件清单落 files.manifest.jsonl(路径/大小/mtime/SHA-256),供校验与下一次增量比对。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BackupFileSnapshot {

    private static final Set<String> SKIP_NAMES = Set.of(".DS_Store", "Thumbs.db", "desktop.ini");

    private final ObjectMapper mapper;

    public record Entry(String path, long size, long mtime, String sha256) {
    }

    public record Result(long fileCount, long logicalBytes, long copiedBytes, String manifestSha256) {
    }

    /**
     * @param sourceRoot      uploads 根目录
     * @param targetDir       本次快照的 files/ 目录
     * @param previousDir     上一次成功快照的 files/ 目录(可空)
     * @param excludePrefixes 相对路径前缀(如备份根目录恰在 uploads 下时排除)
     */
    public Result snapshot(Path sourceRoot, Path targetDir, Path previousDir, Set<String> excludePrefixes) throws IOException {
        Files.createDirectories(targetDir);
        Map<String, Entry> previous = loadPrevious(previousDir);
        List<Path> files = listFiles(sourceRoot, excludePrefixes);

        long total = 0;
        long copied = 0;
        long count = 0;
        Path manifest = targetDir.resolve("files.manifest.jsonl");
        try (BufferedWriter writer = Files.newBufferedWriter(manifest, StandardCharsets.UTF_8)) {
            for (Path file : files) {
                String rel = relative(sourceRoot, file);
                long size = Files.size(file);
                long mtime = Files.getLastModifiedTime(file).toMillis();
                Entry old = previous.get(rel);
                Path target = targetDir.resolve(rel);
                Files.createDirectories(target.getParent());

                String sha;
                if (old != null && old.size() == size && old.mtime() == mtime) {
                    Path oldFile = previousDir.resolve(rel);
                    if (Files.isRegularFile(oldFile)) {
                        try {
                            Files.createLink(target, oldFile);
                            sha = old.sha256();
                        } catch (Exception e) {
                            // 跨磁盘/文件系统不支持硬链接:退回复制,内容哈希沿用上一次结果
                            Files.copy(oldFile, target, StandardCopyOption.REPLACE_EXISTING);
                            copied += size;
                            sha = old.sha256();
                        }
                    } else {
                        sha = copyAndHash(file, target);
                        copied += size;
                    }
                } else {
                    sha = copyAndHash(file, target);
                    copied += size;
                    try {
                        Files.setLastModifiedTime(target, FileTime.fromMillis(mtime));
                    } catch (IOException ignored) {
                        // 时间戳写不进去不影响恢复,下一次会按内容重新复制
                    }
                }
                total += size;
                count++;
                Entry entry = new Entry(rel, size, mtime, sha);
                writer.write(mapper.writeValueAsString(entry));
                writer.newLine();
                if (count % 1000 == 0) log.info("backup snapshot {} files, {} bytes", count, total);
            }
        }

        return new Result(count, total, copied, BackupHash.sha256(manifest));
    }

    /** 单家庭导出:只复制数据库里引用到的文件(相对 uploads 的路径) */
    public Result copyReferenced(Path sourceRoot, Path targetDir, Set<String> relPaths) throws IOException {
        Files.createDirectories(targetDir);
        long total = 0;
        long count = 0;
        Path manifest = targetDir.resolve("files.manifest.jsonl");
        List<String> sorted = new ArrayList<>(relPaths);
        sorted.sort(String::compareTo);
        try (BufferedWriter writer = Files.newBufferedWriter(manifest, StandardCharsets.UTF_8)) {
            for (String rel : sorted) {
                Path source = sourceRoot.resolve(rel).normalize();
                if (!source.startsWith(sourceRoot.normalize()) || !Files.isRegularFile(source)) continue;
                long size = Files.size(source);
                long mtime = Files.getLastModifiedTime(source).toMillis();
                Path target = targetDir.resolve(rel);
                Files.createDirectories(target.getParent());
                String sha = copyAndHash(source, target);
                Files.setLastModifiedTime(target, FileTime.fromMillis(mtime));
                writer.write(mapper.writeValueAsString(new Entry(rel, size, mtime, sha)));
                writer.newLine();
                total += size;
                count++;
            }
        }
        return new Result(count, total, total, BackupHash.sha256(manifest));
    }

    private Map<String, Entry> loadPrevious(Path previousDir) {
        Map<String, Entry> map = new HashMap<>();
        if (previousDir == null) return map;
        Path manifest = previousDir.resolve("files.manifest.jsonl");
        if (!Files.isRegularFile(manifest)) return map;
        try (Stream<String> lines = Files.lines(manifest, StandardCharsets.UTF_8)) {
            lines.forEach(line -> {
                if (line.isBlank()) return;
                try {
                    Entry e = mapper.readValue(line, Entry.class);
                    map.put(e.path(), e);
                } catch (Exception ignored) {
                    // 单行损坏只影响这一条的增量判断,下次会重新复制
                }
            });
        } catch (Exception e) {
            log.warn("read previous backup manifest failed: {}", manifest, e);
        }
        return map;
    }

    private List<Path> listFiles(Path sourceRoot, Set<String> excludePrefixes) throws IOException {
        if (sourceRoot == null || !Files.isDirectory(sourceRoot)) return List.of();
        try (Stream<Path> stream = Files.walk(sourceRoot)) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> !SKIP_NAMES.contains(p.getFileName().toString()))
                    .filter(p -> {
                        String rel = relative(sourceRoot, p);
                        for (String prefix : excludePrefixes) {
                            if (rel.equals(prefix) || rel.startsWith(prefix + "/")) return false;
                        }
                        return true;
                    })
                    .sorted()
                    .toList();
        }
    }

    private static String copyAndHash(Path source, Path target) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        try (DigestInputStream in = new DigestInputStream(Files.newInputStream(source), digest)) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return BackupSql.hex(digest.digest());
    }

    private static String relative(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    /** 读取清单(校验接口用) */
    public List<Entry> readManifest(Path snapshotDir) throws IOException {
        List<Entry> list = new ArrayList<>();
        Path manifest = snapshotDir.resolve("files.manifest.jsonl");
        if (!Files.isRegularFile(manifest)) return list;
        try (Stream<String> lines = Files.lines(manifest, StandardCharsets.UTF_8)) {
            lines.filter(l -> !l.isBlank()).forEach(l -> {
                try {
                    list.add(mapper.readValue(l, Entry.class));
                } catch (Exception ignored) {
                    // 校验时跳过损坏行,由调用方按缺失处理
                }
            });
        }
        return list;
    }
}
