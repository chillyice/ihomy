package com.ihomy.service.backup;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import com.ihomy.entity.SysBackupJob;
import com.ihomy.entity.SysBackupPolicy;
import com.ihomy.mapper.SysBackupJobMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 站内备份/迁移核心。
 *
 * <p>目录布局(备份根默认在 uploads 同级,不暴露给 nginx):</p>
 * <pre>
 * backups/&lt;job-dir&gt;/
 *   meta.json              清单(表行数/文件数/哈希/版本)
 *   RESTORE.md             恢复说明
 *   db/basic|content|full|family-data.sql.gz
 *   files/                 内容备份的 uploads 完整视图(未变文件为硬链接)
 *     files.manifest.jsonl
 * </pre>
 *
 * <p>同一时间只允许一个备份任务(包括定时与手动),避免硬链接基线/磁盘配额互相干扰。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackupService {

    private static final long GB = 1024L * 1024 * 1024;
    private static final DateTimeFormatter DIR_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter HUMAN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SysBackupJobMapper jobMapper;
    private final BackupDumpWriter dumpWriter;
    private final BackupFileSnapshot fileSnapshot;
    private final BackupPolicyService policyService;
    private final ObjectMapper mapper;

    @Value("${file.upload-dir}")
    private String uploadDir;
    @Value("${app.backup.dir:}")
    private String backupDir;
    @Value("${app.backup.min-free-gb:1}")
    private int minFreeGb;
    @Value("${app.backup.max-total-gb:20}")
    private int maxTotalGb;
    @Value("${app.backup.download-max-gb:4}")
    private int downloadMaxGb;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Path uploadRoot;
    private Path backupRoot;

    @PostConstruct
    void init() {
        uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        if (backupDir != null && !backupDir.isBlank()) {
            backupRoot = Paths.get(backupDir).toAbsolutePath().normalize();
        } else {
            Path parent = uploadRoot.getParent();
            backupRoot = (parent == null ? uploadRoot : parent).resolve("backups").normalize();
        }
        try {
            Files.createDirectories(backupRoot);
        } catch (IOException e) {
            log.error("create backup dir failed: {}", backupRoot, e);
        }
        log.info("backup root = {}, upload root = {}", backupRoot, uploadRoot);
    }

    public Path backupRoot() {
        return backupRoot;
    }

    public Path uploadRoot() {
        return uploadRoot;
    }

    /**
     * 创建任务并占位。调用方拿到 job 后必须立刻交给 BackupJobRunner 异步执行;
     * 执行结束(成功/失败)才释放占位。
     */
    public SysBackupJob create(BackupKind kind, BackupScope scope, Long familyId, Long operatorId, String triggerType) {
        return create(kind, scope, familyId, operatorId, triggerType, null, null);
    }

    /** 按策略执行:模块决定导出的表与是否带 uploads 文件 */
    public SysBackupJob createPolicy(SysBackupPolicy policy, Long operatorId, String triggerType) {
        if (policy == null) throw new BizException(ResultCode.BAD_REQUEST, "备份策略不存在");
        String modules = BackupModule.csv(BackupModule.parse(policy.getModules()));
        if (modules.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "策略未选择备份模块,请先编辑策略");
        }
        return create(BackupKind.POLICY, BackupScope.INSTANCE, null, operatorId, triggerType,
                policy.getId(), modules);
    }

    private SysBackupJob create(BackupKind kind, BackupScope scope, Long familyId, Long operatorId,
                                String triggerType, Long policyId, String modules) {
        if (kind == null || scope == null) throw new BizException(ResultCode.BAD_REQUEST, "备份类型或范围不正确");
        if (scope == BackupScope.FAMILY && familyId == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "家庭导出缺少家庭信息");
        }
        if (!running.compareAndSet(false, true)) {
            throw new BizException(ResultCode.CONFLICT, "已有备份任务在执行,请稍后再试");
        }
        try {
            checkDiskSpace();
            LocalDateTime now = LocalDateTime.now();
            String dirName = now.format(DIR_STAMP) + "_" + kind.name().toLowerCase(Locale.ROOT)
                    + "_" + UUID.randomUUID().toString().substring(0, 8);

            SysBackupJob job = new SysBackupJob();
            job.setKind(kind.name());
            job.setScope(scope.name());
            job.setTriggerType(triggerType == null ? "MANUAL" : triggerType);
            job.setStatus(BackupStatus.RUNNING.name());
            job.setFamilyId(familyId);
            job.setPolicyId(policyId);
            job.setModules(modules);
            job.setOperatorId(operatorId);
            job.setDirName(dirName);
            job.setSizeBytes(0L);
            job.setDbBytes(0L);
            job.setFileCount(0);
            job.setDbRowCount(0L);
            job.setDurationMs(0L);
            job.setStartedAt(now);
            job.setCreatedAt(now);
            jobMapper.insert(job);
            log.info("backup job created id={} kind={} scope={} family={}", job.getId(), kind, scope, familyId);
            return job;
        } catch (RuntimeException e) {
            running.set(false);
            throw e;
        }
    }

    /** 异步执行体(由 BackupJobRunner 调用) */
    public void execute(Long jobId) {
        SysBackupJob job = jobMapper.selectById(jobId);
        if (job == null) {
            running.set(false);
            return;
        }
        long begin = System.currentTimeMillis();
        Path dir = backupRoot.resolve(job.getDirName());
        try {
            Files.createDirectories(dir.resolve("db"));
            BackupKind kind = BackupKind.valueOf(job.getKind());
            BackupScope scope = BackupScope.valueOf(job.getScope());

            BackupDumpWriter.DumpResult dump;
            BackupFileSnapshot.Result files = null;
            List<String> modules = BackupModule.parse(job.getModules());
            Set<String> policyTables = kind == BackupKind.POLICY ? BackupModule.tablesFor(modules) : null;
            boolean withFiles = kind == BackupKind.POLICY
                    ? BackupModule.includesFiles(modules)
                    : kind.includesFiles();
            if (scope == BackupScope.FAMILY) {
                dump = dumpWriter.write(dir.resolve("db").resolve("family-data.sql.gz"), kind, job.getFamilyId());
                if (!dump.fileRefs().isEmpty()) {
                    files = fileSnapshot.copyReferenced(uploadRoot, dir.resolve("files"), dump.fileRefs());
                }
            } else {
                String dbName = switch (kind) {
                    case BASIC -> "basic";
                    case CONTENT -> "content";
                    case POLICY -> "policy";
                    default -> "full";
                };
                dump = dumpWriter.write(dir.resolve("db").resolve(dbName + ".sql.gz"), kind, null, policyTables);
                if (withFiles) {
                    Path previous = previousFilesDir(job);
                    files = fileSnapshot.snapshot(uploadRoot, dir.resolve("files"), previous, excludePrefixes());
                }
            }

            LocalDateTime finished = LocalDateTime.now();
            String metaHash = writeMeta(job, dir, dump, files, finished);
            long size = BackupHash.directorySize(dir);
            job.setStatus(BackupStatus.SUCCESS.name());
            job.setFinishedAt(finished);
            job.setDurationMs(Duration.between(job.getStartedAt(), finished).toMillis());
            job.setSizeBytes(size);
            job.setDbBytes(dump.bytes());
            job.setDbRowCount(dump.rows());
            job.setFileCount(files == null ? 0 : (int) files.fileCount());
            job.setManifestSha256(metaHash);
            job.setMessage(summaryMessage(job, dump, files));
            jobMapper.updateById(job);
            log.info("backup job done id={} size={} files={} rows={} cost={}ms",
                    jobId, size, job.getFileCount(), job.getDbRowCount(), job.getDurationMs());

            try {
                purgeByRetention();
            } catch (Exception e) {
                log.warn("backup retention purge failed after job {}", jobId, e);
            }
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - begin;
            log.error("backup job failed id={} kind={}", jobId, job.getKind(), e);
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            if (msg.length() > 900) msg = msg.substring(0, 900);
            jobMapper.update(null, new LambdaUpdateWrapper<SysBackupJob>()
                    .eq(SysBackupJob::getId, jobId)
                    .set(SysBackupJob::getStatus, BackupStatus.FAILED.name())
                    .set(SysBackupJob::getFinishedAt, LocalDateTime.now())
                    .set(SysBackupJob::getDurationMs, cost)
                    .set(SysBackupJob::getMessage, msg));
            deleteDirectory(dir);
        } finally {
            running.set(false);
        }
    }

    public IPage<SysBackupJob> page(int current, int size, String kind, String status,
                                    BackupScope scope, Long familyId, Long policyId) {
        LambdaQueryWrapper<SysBackupJob> w = new LambdaQueryWrapper<SysBackupJob>()
                .eq(scope != null, SysBackupJob::getScope, scope == null ? null : scope.name())
                .eq(familyId != null, SysBackupJob::getFamilyId, familyId)
                .eq(policyId != null, SysBackupJob::getPolicyId, policyId)
                .eq(kind != null && !kind.isBlank(), SysBackupJob::getKind, kind)
                .eq(status != null && !status.isBlank(), SysBackupJob::getStatus, status)
                .orderByDesc(SysBackupJob::getId);
        return jobMapper.selectPage(new Page<>(current, Math.min(Math.max(size, 1), 100)), w);
    }

    public Map<String, Object> overview() {
        List<SysBackupPolicy> policies = policyService.list();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("root", backupRoot.toString());
        out.put("running", running.get());
        out.put("policyCount", policies.size());
        out.put("policyEnabledCount", policies.stream()
                .filter(p -> !Boolean.FALSE.equals(p.getEnabled())).count());
        Map<String, Object> limits = new LinkedHashMap<>();
        limits.put("minFreeGb", minFreeGb);
        limits.put("maxTotalGb", maxTotalGb);
        limits.put("downloadMaxGb", downloadMaxGb);
        out.put("limits", limits);

        long diskBytes = 0;
        long logicalBytes = 0;
        try {
            diskBytes = BackupHash.diskUsage(backupRoot);
            logicalBytes = BackupHash.directorySize(backupRoot);
        } catch (IOException e) {
            log.warn("stat backup root failed", e);
        }
        out.put("diskBytes", diskBytes);
        out.put("logicalBytes", logicalBytes);
        try {
            FileStore store = Files.getFileStore(backupRoot);
            out.put("diskTotalBytes", store.getTotalSpace());
            out.put("diskFreeBytes", store.getUsableSpace());
        } catch (IOException e) {
            out.put("diskTotalBytes", 0);
            out.put("diskFreeBytes", 0);
        }

        Map<String, Object> lastSuccess = new LinkedHashMap<>();
        for (BackupKind kind : BackupKind.values()) {
            SysBackupJob job = jobMapper.selectOne(new LambdaQueryWrapper<SysBackupJob>()
                    .eq(SysBackupJob::getScope, BackupScope.INSTANCE.name())
                    .eq(SysBackupJob::getKind, kind.name())
                    .eq(SysBackupJob::getStatus, BackupStatus.SUCCESS.name())
                    .orderByDesc(SysBackupJob::getStartedAt)
                    .orderByDesc(SysBackupJob::getId)
                    .last("LIMIT 1"));
            if (job != null) lastSuccess.put(kind.name(), brief(job));
        }
        out.put("lastSuccess", lastSuccess);
        return out;
    }

    private Map<String, Object> brief(SysBackupJob job) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", job.getId());
        m.put("startedAt", job.getStartedAt() == null ? null : job.getStartedAt().format(HUMAN));
        m.put("sizeBytes", job.getSizeBytes());
        m.put("status", job.getStatus());
        return m;
    }

    /** 校验一份备份:meta 哈希、SQL gzip 完整性、文件清单是否齐全 */
    public Map<String, Object> verify(Long id) {
        SysBackupJob job = mustGet(id);
        Map<String, Object> out = new LinkedHashMap<>();
        if (!BackupStatus.SUCCESS.name().equals(job.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "只有已完成的备份可以校验");
        }
        Path dir = backupRoot.resolve(job.getDirName());
        if (!Files.isDirectory(dir)) {
            out.put("ok", false);
            out.put("message", "备份文件已不存在(可能已被清理)");
            return out;
        }

        Path meta = dir.resolve("meta.json");
        boolean metaOk = Files.isRegularFile(meta);
        if (metaOk && job.getManifestSha256() != null) {
            try {
                metaOk = job.getManifestSha256().equalsIgnoreCase(BackupHash.sha256(meta));
            } catch (IOException e) {
                metaOk = false;
            }
        }

        long dbBytes = 0;
        boolean dbOk = false;
        Path dbDir = dir.resolve("db");
        try (Stream<Path> stream = Files.list(dbDir)) {
            Path sql = stream.filter(p -> p.getFileName().toString().endsWith(".sql.gz")).findFirst().orElse(null);
            if (sql != null) {
                try (InputStream in = new GZIPInputStream(new BufferedInputStream(Files.newInputStream(sql)))) {
                    byte[] buf = new byte[1 << 16];
                    int n;
                    while ((n = in.read(buf)) > 0) dbBytes += n;
                    dbOk = true;
                }
            }
        } catch (IOException e) {
            dbOk = false;
        }

        int fileCount = 0;
        int missing = 0;
        Path filesDir = dir.resolve("files");
        if (Files.isDirectory(filesDir)) {
            try {
                List<BackupFileSnapshot.Entry> entries = fileSnapshot.readManifest(filesDir);
                fileCount = entries.size();
                for (BackupFileSnapshot.Entry e : entries) {
                    Path p = filesDir.resolve(e.path());
                    if (!Files.isRegularFile(p)) missing++;
                }
            } catch (IOException e) {
                missing = -1;
            }
        }

        boolean ok = metaOk && dbOk && missing == 0;
        out.put("ok", ok);
        out.put("metaOk", metaOk);
        out.put("dbOk", dbOk);
        out.put("dbBytes", dbBytes);
        out.put("fileCount", fileCount);
        out.put("missingFiles", missing);
        out.put("message", ok ? "校验通过" : "发现损坏或缺失,请检查备份目录");
        return out;
    }

    /** 删除备份文件(保留审计记录) */
    public void deleteJob(Long id) {
        SysBackupJob job = mustGet(id);
        if (BackupStatus.RUNNING.name().equals(job.getStatus())) {
            throw new BizException(ResultCode.CONFLICT, "备份正在执行,暂时不能删除");
        }
        Path dir = backupRoot.resolve(job.getDirName());
        deleteDirectory(dir);
        jobMapper.update(null, new LambdaUpdateWrapper<SysBackupJob>()
                .eq(SysBackupJob::getId, id)
                .set(SysBackupJob::getStatus, BackupStatus.PURGED.name())
                .set(SysBackupJob::getPurgedAt, LocalDateTime.now())
                .set(SysBackupJob::getMessage, "已手动删除备份文件"));
    }

    public SysBackupJob mustGet(Long id) {
        SysBackupJob job = id == null ? null : jobMapper.selectById(id);
        if (job == null) throw new BizException(ResultCode.NOT_FOUND, "备份任务不存在");
        return job;
    }

    public void checkFamilyDiaryPrivacy(SysBackupJob job) throws IOException {
        if (!BackupStatus.SUCCESS.name().equals(job.getStatus())) return;
        Path metadata = backupRoot.resolve(job.getDirName()).resolve("meta.json");
        if (Files.isRegularFile(metadata)) {
            var meta = mapper.readTree(Files.readString(metadata, StandardCharsets.UTF_8));
            if (meta.path("privateDiariesExcluded").asBoolean(false)
                    || meta.path("database").path("tables").path("content_diary").asLong(-1) == 0) return;
        }
        throw new BizException(ResultCode.BAD_REQUEST, "请重新生成家庭数据包，以保护成员的私密日记");
    }

    public void streamZip(Long id, OutputStream out) {
        SysBackupJob job = mustGet(id);
        if (!BackupStatus.SUCCESS.name().equals(job.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "只有已完成的备份可以下载");
        }
        Path dir = backupRoot.resolve(job.getDirName());
        if (!Files.isDirectory(dir)) {
            throw new BizException(ResultCode.NOT_FOUND, "备份文件已不存在(可能已被清理)");
        }
        try {
            long size = BackupHash.directorySize(dir);
            if (downloadMaxGb > 0 && size > downloadMaxGb * GB) {
                throw new BizException(ResultCode.BAD_REQUEST,
                        "备份包超过站内下载上限(" + downloadMaxGb + "GB),请直接在服务器上复制备份目录:" + dir);
            }
            try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8);
                 Stream<Path> stream = Files.walk(dir)) {
                List<Path> files = stream.filter(Files::isRegularFile)
                        .sorted(Comparator.comparing(Path::toString))
                        .toList();
                for (Path p : files) {
                    String name = dir.relativize(p).toString().replace('\\', '/');
                    ZipEntry entry = new ZipEntry(name);
                    entry.setTime(Files.getLastModifiedTime(p).toMillis());
                    zip.putNextEntry(entry);
                    Files.copy(p, zip);
                    zip.closeEntry();
                }
                zip.finish();
            }
        } catch (BizException e) {
            throw e;
        } catch (IOException e) {
            log.error("stream backup zip failed id={}", id, e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "备份包下载失败");
        }
    }

    /** 日常清理入口(每个任务成功后 + 每天 05:00 定时各跑一次) */
    public void purgeByRetention() {
        LocalDateTime now = LocalDateTime.now();

        // 1) 每条策略按自己的保留天数清理;留空 = 不自动清理
        List<SysBackupPolicy> policies = policyService.list();
        Set<Long> policyIds = new java.util.HashSet<>();
        for (SysBackupPolicy policy : policies) {
            policyIds.add(policy.getId());
            if (policy.getRetentionDays() == null) continue;
            List<SysBackupJob> jobs = policyJobs(policy.getId());
            purge(BackupRetention.selectByDays(jobs, policy.getRetentionDays(), now).purge());
        }

        // 2) 已被删除策略的历史任务:按 30 天兜底,避免文件永远占空间(策略全删时也照此清理)
        LambdaQueryWrapper<SysBackupJob> orphanQuery = new LambdaQueryWrapper<SysBackupJob>()
                .eq(SysBackupJob::getScope, BackupScope.INSTANCE.name())
                .eq(SysBackupJob::getStatus, BackupStatus.SUCCESS.name())
                .isNotNull(SysBackupJob::getPolicyId)
                .orderByDesc(SysBackupJob::getStartedAt)
                .orderByDesc(SysBackupJob::getId);
        if (!policyIds.isEmpty()) orphanQuery.notIn(SysBackupJob::getPolicyId, policyIds);
        Map<Long, List<SysBackupJob>> byPolicy = new LinkedHashMap<>();
        for (SysBackupJob job : jobMapper.selectList(orphanQuery)) {
            byPolicy.computeIfAbsent(job.getPolicyId(), k -> new ArrayList<>()).add(job);
        }
        for (List<SysBackupJob> list : byPolicy.values()) {
            purge(BackupRetention.selectByDays(list, 30, now).purge());
        }

        // 3) 旧版固定策略(policy_id IS NULL):沿用 GFS 默认保留,迁移包永不自动清理
        List<SysBackupJob> legacy = jobMapper.selectList(new LambdaQueryWrapper<SysBackupJob>()
                .eq(SysBackupJob::getScope, BackupScope.INSTANCE.name())
                .eq(SysBackupJob::getStatus, BackupStatus.SUCCESS.name())
                .isNull(SysBackupJob::getPolicyId)
                .orderByDesc(SysBackupJob::getStartedAt)
                .orderByDesc(SysBackupJob::getId));
        purge(selectFor(legacy, BackupKind.BASIC.name(), 7, 4, 3));
        purge(selectFor(legacy, BackupKind.CONTENT.name(), 7, 4, 3));
        purge(selectFor(legacy, BackupKind.FULL.name(), 0, 8, 12));

        // 4) 旧版家庭导出:每个家庭最多 3 份、只保留最近 30 天
        List<SysBackupJob> family = jobMapper.selectList(new LambdaQueryWrapper<SysBackupJob>()
                .eq(SysBackupJob::getScope, BackupScope.FAMILY.name())
                .eq(SysBackupJob::getStatus, BackupStatus.SUCCESS.name())
                .orderByDesc(SysBackupJob::getStartedAt)
                .orderByDesc(SysBackupJob::getId));
        Map<Long, List<SysBackupJob>> byFamily = new LinkedHashMap<>();
        for (SysBackupJob job : family) {
            byFamily.computeIfAbsent(job.getFamilyId(), k -> new ArrayList<>()).add(job);
        }
        for (List<SysBackupJob> list : byFamily.values()) {
            purge(BackupRetention.selectFamily(list, 3, 30, now).purge());
        }
    }

    private List<SysBackupJob> policyJobs(Long policyId) {
        return jobMapper.selectList(new LambdaQueryWrapper<SysBackupJob>()
                .eq(SysBackupJob::getScope, BackupScope.INSTANCE.name())
                .eq(SysBackupJob::getPolicyId, policyId)
                .eq(SysBackupJob::getStatus, BackupStatus.SUCCESS.name())
                .orderByDesc(SysBackupJob::getStartedAt)
                .orderByDesc(SysBackupJob::getId));
    }

    private List<Long> selectFor(List<SysBackupJob> all, String kind, int daily, int weekly, int monthly) {
        List<SysBackupJob> subset = new ArrayList<>();
        for (SysBackupJob job : all) {
            if (kind.equals(job.getKind())) subset.add(job);
        }
        if (subset.isEmpty()) return List.of();
        return BackupRetention.select(subset, daily, weekly, monthly).purge();
    }

    private void purge(List<Long> ids) {
        for (Long id : ids) {
            SysBackupJob job = jobMapper.selectById(id);
            if (job == null) continue;
            Path dir = backupRoot.resolve(job.getDirName());
            try {
                deleteDirectory(dir);
                jobMapper.update(null, new LambdaUpdateWrapper<SysBackupJob>()
                        .eq(SysBackupJob::getId, id)
                        .set(SysBackupJob::getStatus, BackupStatus.PURGED.name())
                        .set(SysBackupJob::getPurgedAt, LocalDateTime.now())
                        .set(SysBackupJob::getMessage, "已按保留策略自动清理"));
                log.info("backup purged id={} dir={}", id, job.getDirName());
            } catch (Exception e) {
                log.warn("purge backup files failed id={} dir={}", id, dir, e);
            }
        }
    }

    /** 服务重启时把遗留的 RUNNING 标记为失败,避免永久占位 */
    public void markInterrupted() {
        jobMapper.update(null, new LambdaUpdateWrapper<SysBackupJob>()
                .eq(SysBackupJob::getStatus, BackupStatus.RUNNING.name())
                .set(SysBackupJob::getStatus, BackupStatus.FAILED.name())
                .set(SysBackupJob::getFinishedAt, LocalDateTime.now())
                .set(SysBackupJob::getMessage, "服务重启,任务中断"));
        running.set(false);
    }

    public boolean isRunning() {
        return running.get();
    }

    /* ------------------------------------------------------------ 内部 */

    private void checkDiskSpace() {
        try {
            Files.createDirectories(backupRoot);
            FileStore store = Files.getFileStore(backupRoot);
            long need = minFreeGb * GB + 256L * 1024 * 1024;
            if (store.getUsableSpace() < need) {
                throw new BizException(ResultCode.CONFLICT,
                        "磁盘可用空间不足(至少需要 " + (minFreeGb + 0.25) + "GB),请先清理旧备份或释放磁盘");
            }
            if (maxTotalGb > 0) {
                long used = BackupHash.diskUsage(backupRoot);
                if (used > maxTotalGb * GB) {
                    throw new BizException(ResultCode.CONFLICT,
                            "备份目录已超过上限(" + maxTotalGb + "GB),请先在列表中删除旧备份");
                }
            }
        } catch (BizException e) {
            throw e;
        } catch (IOException e) {
            log.error("check backup disk failed", e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "备份目录不可用,请检查服务器磁盘与权限");
        }
    }

    /** 找最近一份真正带 uploads 快照的任务作为硬链接基线(策略可能只备数据库) */
    private Path previousFilesDir(SysBackupJob current) {
        List<SysBackupJob> prevs = jobMapper.selectList(new LambdaQueryWrapper<SysBackupJob>()
                .eq(SysBackupJob::getScope, BackupScope.INSTANCE.name())
                .eq(SysBackupJob::getStatus, BackupStatus.SUCCESS.name())
                .in(SysBackupJob::getKind, List.of(
                        BackupKind.CONTENT.name(), BackupKind.FULL.name(), BackupKind.MIGRATION.name(),
                        BackupKind.POLICY.name()))
                .lt(SysBackupJob::getId, current.getId())
                .orderByDesc(SysBackupJob::getId)
                .last("LIMIT 20"));
        for (SysBackupJob prev : prevs) {
            Path files = backupRoot.resolve(prev.getDirName()).resolve("files");
            if (Files.isDirectory(files)) return files;
        }
        return null;
    }

    private java.util.Set<String> excludePrefixes() {
        java.util.Set<String> prefixes = new java.util.HashSet<>();
        if (backupRoot.startsWith(uploadRoot)) {
            String rel = uploadRoot.relativize(backupRoot).toString().replace('\\', '/');
            if (!rel.isBlank()) prefixes.add(rel);
        }
        return prefixes;
    }

    private String writeMeta(SysBackupJob job, Path dir, BackupDumpWriter.DumpResult dump,
                             BackupFileSnapshot.Result files, LocalDateTime finished) throws IOException {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("app", "ihomy");
        meta.put("version", appVersion());
        meta.put("kind", job.getKind());
        meta.put("scope", job.getScope());
        if (BackupScope.FAMILY.name().equals(job.getScope())) meta.put("privateDiariesExcluded", true);
        meta.put("familyId", job.getFamilyId());
        meta.put("policyId", job.getPolicyId());
        if (job.getModules() != null) meta.put("modules", BackupModule.parse(job.getModules()));
        meta.put("startedAt", job.getStartedAt().format(HUMAN));
        meta.put("finishedAt", finished.format(HUMAN));

        Map<String, Object> db = new LinkedHashMap<>();
        db.put("file", dbFileName(dir));
        db.put("bytes", dump.bytes());
        db.put("sha256", dump.sha256());
        db.put("tables", dump.tables());
        db.put("rows", dump.rows());
        meta.put("database", db);

        Map<String, Object> fileInfo = new LinkedHashMap<>();
        fileInfo.put("count", files == null ? 0 : files.fileCount());
        fileInfo.put("bytes", files == null ? 0 : files.logicalBytes());
        fileInfo.put("copiedBytes", files == null ? 0 : files.copiedBytes());
        fileInfo.put("manifest", files == null ? null : "files/files.manifest.jsonl");
        meta.put("files", fileInfo);

        Path metaFile = dir.resolve("meta.json");
        Files.writeString(metaFile, mapper.writeValueAsString(meta), StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("RESTORE.md"), restoreGuide(job, dump, files), StandardCharsets.UTF_8);
        return BackupHash.sha256(metaFile);
    }

    private String dbFileName(Path dir) throws IOException {
        Path dbDir = dir.resolve("db");
        try (Stream<Path> stream = Files.list(dbDir)) {
            return stream.filter(p -> p.getFileName().toString().endsWith(".sql.gz"))
                    .findFirst()
                    .map(p -> "db/" + p.getFileName())
                    .orElse("db/");
        }
    }

    private String restoreGuide(SysBackupJob job, BackupDumpWriter.DumpResult dump,
                                BackupFileSnapshot.Result files) {
        String dbFile = job.getScope().equals(BackupScope.FAMILY.name())
                ? "db/family-data.sql.gz" : "db/" + switch (BackupKind.valueOf(job.getKind())) {
            case BASIC -> "basic";
            case CONTENT -> "content";
            case POLICY -> "policy";
            default -> "full";
        } + ".sql.gz";
        StringBuilder sb = new StringBuilder();
        sb.append("# ihomy 备份包恢复说明\n\n");
        sb.append("- 类型:").append(job.getKind()).append(" / ").append(job.getScope()).append('\n');
        sb.append("- 生成时间:").append(job.getStartedAt().format(HUMAN)).append('\n');
        sb.append("- 数据库:").append(dbFile).append("(").append(dump.rows()).append(" 行)\n");
        sb.append("- 文件:").append(files == null ? 0 : files.fileCount()).append(" 个\n\n");
        sb.append("## 恢复到新服务器(Linux)\n\n");
        sb.append("1. 停后端:`sudo systemctl stop ihomy-backend`\n");
        sb.append("2. 建空库并导入:\n\n");
        sb.append("   ```bash\n");
        sb.append("   mysql -uroot -p -e \"CREATE DATABASE IF NOT EXISTS ihomy CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;\"\n");
        sb.append("   gunzip -c ").append(dbFile).append(" | mysql -uroot -p ihomy\n");
        sb.append("   ```\n\n");
        sb.append("3. 恢复上传文件:\n\n");
        sb.append("   ```bash\n");
        sb.append("   rsync -a files/ /opt/ihomy/uploads/\n");
        sb.append("   ```\n\n");
        if (job.getScope().equals(BackupScope.FAMILY.name())) {
            sb.append("4. 本包是**单家庭导出**,保留原始 family_id/user_id,建议只恢复到全新空实例;\n");
            sb.append("   目标库已有数据时,请先核对家庭与用户 ID 是否冲突再导入。\n");
        } else if ("BASIC".equals(job.getKind())) {
            sb.append("4. 本包只含基础数据,不含上传文件;文件请从同一时期的 CONTENT/FULL 备份恢复。\n");
        } else if ("CONTENT".equals(job.getKind())) {
            sb.append("4. 本包只含内容表与上传文件;基础数据请从同一时期的 BASIC/FULL 备份恢复。\n");
        } else if ("POLICY".equals(job.getKind())) {
            sb.append("4. 本包由备份策略生成,只含勾选的模块:");
            sb.append(job.getModules() == null ? "—" : job.getModules());
            sb.append(";需要独立恢复整站时请使用包含全部模块的策略或迁移包。\n");
        } else {
            sb.append("4. 本包含全量数据库与上传文件,可独立恢复整站。\n");
        }
        sb.append("\n> 生产恢复前先做一次当前库快照:`scripts/prod-restore.sh` 会自动执行这一步。\n");
        return sb.toString();
    }

    private String summaryMessage(SysBackupJob job, BackupDumpWriter.DumpResult dump,
                                  BackupFileSnapshot.Result files) {
        return "数据库 " + dump.rows() + " 行 / 文件 " + (files == null ? 0 : files.fileCount())
                + " 个 / 新增落盘 " + (files == null ? 0 : files.copiedBytes()) + " 字节";
    }

    private String appVersion() {
        try {
            Path version = Paths.get("VERSION");
            if (Files.isRegularFile(version)) return Files.readString(version, StandardCharsets.UTF_8).trim();
        } catch (IOException ignored) {
            // 版本只用于清单标识,读不到不影响备份
        }
        return "unknown";
    }

    private static void deleteDirectory(Path dir) {
        if (dir == null || !Files.exists(dir)) return;
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.warn("delete backup path failed: {}", p, e);
                }
            });
        } catch (IOException e) {
            log.warn("walk backup dir failed: {}", dir, e);
        }
    }

    /** 供测试/运维排查:列出某任务目录下的文件 */
    public List<String> listFiles(Long id) throws IOException {
        SysBackupJob job = mustGet(id);
        Path dir = backupRoot.resolve(job.getDirName());
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> stream = Files.walk(dir)) {
            return stream.filter(Files::isRegularFile)
                    .map(p -> dir.relativize(p).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        }
    }

    // 供单测/维护工具使用
    static Map<String, Object> emptyMeta() {
        return new HashMap<>();
    }
}
