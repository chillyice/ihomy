package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ihomy.entity.*;
import com.ihomy.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 运维服务（V3.8）:
 * - stats:   各资源表总数量（可按创建时间/用户/家庭过滤）
 * - server:  服务器/JVM 状态（内存、线程、磁盘用量）
 * - logs:    全系统操作日志检索（带过滤分页）
 * 本类仅返回系统级聚合信息,不涉及任何用户/家庭内容明细。
 */
@Service
@RequiredArgsConstructor
public class OpsService {

    private final SysUserMapper sysUserMapper;
    private final FamilyMapper familyMapper;
    private final BlogMapper blogMapper;
    private final DiaryMapper diaryMapper;
    private final AlbumMapper albumMapper;
    private final PhotoMapper photoMapper;
    private final VideoMapper videoMapper;
    private final CommentMapper commentMapper;
    private final ContentLikeMapper likeMapper;
    private final CheckinMapper checkinMapper;
    private final FamilyPlanMapper planMapper;
    private final WishMapper wishMapper;
    private final BookRecordMapper bookMapper;
    private final ReminderMapper reminderMapper;
    private final SysOperationLogMapper logMapper;

    /** 日志根目录(logging.file.path,与 logback-spring.xml 一致) */
    @Value("${logging.file.path:logs}")
    private String logRoot;

    /** 日志行头:时间 [线程] [tid:xxx] LEVEL logger - 消息 */
    private static final Pattern LOG_HEADER = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}) \\[([^\\]]*)\\] \\[tid:([^\\]]*)\\] +([A-Z]+) +(\\S+) - (.*)$");
    /** 单条消息返回上限(防超长 SQL/堆栈撑爆响应) */
    private static final int MAX_ENTRY_MSG = 16384;
    /** 返回条数上限 */
    private static final int MAX_ENTRIES = 3000;

    /** 各资源统计:可按开始/结束日期(含)、用户 ID、家庭 ID 过滤;缺省为全量 */
    public Map<String, Long> stats(LocalDate startDate, LocalDate endDate, Long userId, Long familyId) {
        LocalDateTime start = startDate == null ? null : startDate.atStartOfDay();
        LocalDateTime end = endDate == null ? null : endDate.plusDays(1).atStartOfDay().minusSeconds(1);

        Map<String, Long> m = new LinkedHashMap<>();
        m.put("users", sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .ge(start != null, SysUser::getCreatedAt, start).le(end != null, SysUser::getCreatedAt, end)));
        m.put("families", familyMapper.selectCount(new LambdaQueryWrapper<Family>()
                .ge(start != null, Family::getCreatedAt, start).le(end != null, Family::getCreatedAt, end)));
        m.put("blogs", blogMapper.selectCount(filt(new LambdaQueryWrapper<Blog>(),
                start, end, userId, familyId, Blog::getCreatedAt, Blog::getAuthorId, Blog::getFamilyId)));
        m.put("diaries", diaryMapper.selectCount(filt(new LambdaQueryWrapper<Diary>(),
                start, end, userId, familyId, Diary::getCreatedAt, Diary::getAuthorId, Diary::getFamilyId)));
        m.put("albums", albumMapper.selectCount(filt(new LambdaQueryWrapper<Album>(),
                start, end, userId, familyId, Album::getCreatedAt, Album::getCreatedBy, Album::getFamilyId)));
        m.put("photos", photoMapper.selectCount(filt(new LambdaQueryWrapper<Photo>(),
                start, end, userId, familyId, Photo::getCreatedAt, Photo::getAuthorId, Photo::getFamilyId)));
        m.put("videos", videoMapper.selectCount(filt(new LambdaQueryWrapper<Video>(),
                start, end, userId, familyId, Video::getCreatedAt, Video::getUploaderId, Video::getFamilyId)));
        m.put("comments", commentMapper.selectCount(filt(new LambdaQueryWrapper<Comment>(),
                start, end, userId, familyId, Comment::getCreatedAt, Comment::getAuthorId, Comment::getFamilyId)));
        m.put("likes", likeMapper.selectCount(filt(new LambdaQueryWrapper<ContentLike>(),
                start, end, userId, familyId, ContentLike::getCreatedAt, ContentLike::getUserId, ContentLike::getFamilyId)));
        m.put("checkins", checkinMapper.selectCount(filt(new LambdaQueryWrapper<Checkin>(),
                start, end, userId, familyId, Checkin::getCreatedAt, Checkin::getUserId, Checkin::getFamilyId)));
        m.put("plans", planMapper.selectCount(filt(new LambdaQueryWrapper<FamilyPlan>(),
                start, end, userId, familyId, FamilyPlan::getCreatedAt, FamilyPlan::getCreatedBy, FamilyPlan::getFamilyId)));
        m.put("wishes", wishMapper.selectCount(filt(new LambdaQueryWrapper<Wish>(),
                start, end, userId, familyId, Wish::getCreatedAt, Wish::getRequesterId, Wish::getFamilyId)));
        m.put("bookRecords", bookMapper.selectCount(filt(new LambdaQueryWrapper<BookRecord>(),
                start, end, userId, familyId, BookRecord::getCreatedAt, BookRecord::getCreatedBy, BookRecord::getFamilyId)));
        m.put("reminders", reminderMapper.selectCount(filt(new LambdaQueryWrapper<Reminder>(),
                start, end, userId, familyId, Reminder::getCreatedAt, Reminder::getCreatedBy, Reminder::getFamilyId)));
        m.put("operationLogs", logMapper.selectCount(filt(new LambdaQueryWrapper<SysOperationLog>(),
                start, end, userId, null, SysOperationLog::getCreatedAt, SysOperationLog::getOperatorId, null)));
        return m;
    }

    /** 组装通用过滤条件(时间/用户/家庭均可选;列引用为空则跳过对应过滤) */
    private <T> LambdaQueryWrapper<T> filt(LambdaQueryWrapper<T> w,
                                           LocalDateTime start, LocalDateTime end,
                                           Long userId, Long familyId,
                                           SFunction<T, ?> createdAtCol,
                                           SFunction<T, ?> userCol,
                                           SFunction<T, ?> famCol) {
        if (start != null) w.ge(createdAtCol, start);
        if (end != null) w.le(createdAtCol, end);
        if (userId != null && userCol != null) w.eq(userCol, userId);
        if (familyId != null && famCol != null) w.eq(famCol, familyId);
        return w;
    }

    /**
     * 服务器状态:JVM 内存/线程/运行时长 + OS 摘要 + 磁盘用量。
     * 数据来自 JDK(ManagementFactory/FileStore),无需外部监控组件。
     */
    public Map<String, Object> server() {
        Runtime rt = Runtime.getRuntime();
        long used = rt.totalMemory() - rt.freeMemory();
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();

        Map<String, Object> jvm = new LinkedHashMap<>();
        jvm.put("heapUsed", used);
        jvm.put("heapMax", rt.maxMemory());
        jvm.put("heapCommitted", rt.totalMemory());
        jvm.put("threads", threadBean.getThreadCount());
        jvm.put("uptimeSec", ManagementFactory.getRuntimeMXBean().getUptime() / 1000);
        jvm.put("javaVersion", System.getProperty("java.version"));

        Map<String, Object> os = new LinkedHashMap<>();
        os.put("name", System.getProperty("os.name"));
        os.put("arch", System.getProperty("os.arch"));
        os.put("cores", rt.availableProcessors());
        os.put("loadAvg", osBean.getSystemLoadAverage());

        // 磁盘挂载点用量(仅根分区,对运维有意义的层)
        List<Map<String, Object>> disks = new ArrayList<>();
        for (File root : File.listRoots()) {
            try {
                FileStore store = Files.getFileStore(root.toPath());
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("path", root.getPath());
                d.put("total", store.getTotalSpace());
                d.put("free", store.getUsableSpace());
                d.put("type", store.type());
                disks.add(d);
            } catch (Exception ignored) {
                // 个别挂载点不可读则跳过
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("jvm", jvm);
        result.put("os", os);
        result.put("disks", disks);
        result.put("time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return result;
    }

    /** 操作日志检索:过滤分页(时间/操作人/模块/操作类型/关键字),时间倒序 */
    public IPage<SysOperationLog> logs(int current, int size, Long operatorId, String module,
                                       String operationType, LocalDate startDate, LocalDate endDate,
                                       String keyword) {
        LocalDateTime start = startDate == null ? null : startDate.atStartOfDay();
        LocalDateTime end = endDate == null ? null : endDate.plusDays(1).atStartOfDay();
        LambdaQueryWrapper<SysOperationLog> w = new LambdaQueryWrapper<SysOperationLog>()
                .eq(operatorId != null, SysOperationLog::getOperatorId, operatorId)
                .eq(module != null && !module.isBlank(), SysOperationLog::getModule, module)
                .eq(operationType != null && !operationType.isBlank(), SysOperationLog::getOperationType, operationType)
                .ge(start != null, SysOperationLog::getCreatedAt, start)
                .lt(end != null, SysOperationLog::getCreatedAt, end)
                .and(keyword != null && !keyword.isBlank(), kw -> kw
                        .like(SysOperationLog::getDescription, keyword)
                        .or().like(SysOperationLog::getRequestUrl, keyword)
                        .or().like(SysOperationLog::getOperatorName, keyword))
                .orderByDesc(SysOperationLog::getCreatedAt);
        return logMapper.selectPage(new Page<>(current, size), w);
    }

    /**
     * 详细日志:按 tid 检索当日(或指定日期)三类日志文件(access/server/thirdparty),
     * 解析成结构化条目并按时间排序合并。文件按天滚动,当天查活跃文件、历史查滚动文件。
     */
    public Map<String, Object> traceLogs(String tid, LocalDate date) {
        if (tid == null || !tid.matches("[a-zA-Z0-9-]{6,64}")) {
            throw new com.ihomy.common.BizException(com.ihomy.common.ResultCode.BAD_REQUEST, "tid 格式不合法");
        }
        LocalDate day = date == null ? LocalDate.now() : date;
        String suffix = day.equals(LocalDate.now()) ? "" : "." + day.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        List<Map<String, Object>> entries = new ArrayList<>();
        boolean truncated = false;
        for (String source : List.of("access", "server", "thirdparty")) {
            Path file = Path.of(logRoot, source, "ihomy-" + source + suffix + ".log");
            if (!Files.exists(file)) {
                continue;
            }
            entries.addAll(parseAndFilter(file, source, tid));
            if (entries.size() > MAX_ENTRIES) {
                truncated = true;
                entries = new ArrayList<>(entries.subList(0, MAX_ENTRIES));
                break;
            }
        }
        entries.sort(Comparator.comparing(e -> (String) e.get("time")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tid", tid);
        result.put("date", day.toString());
        result.put("count", entries.size());
        result.put("truncated", truncated);
        result.put("entries", entries);
        return result;
    }

    /** 解析单个日志文件,保留 tid 精确匹配的条目(堆栈等续行归入上一条消息) */
    private List<Map<String, Object>> parseAndFilter(Path file, String source, String tid) {
        List<Map<String, Object>> out = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            Map<String, Object> cur = null;
            StringBuilder curMsg = null;
            while ((line = br.readLine()) != null) {
                Matcher header = LOG_HEADER.matcher(line);
                if (header.matches()) {
                    if (cur != null && tid.equals(cur.get("tid"))) {
                        cur.put("message", truncateMsg(curMsg.toString()));
                        out.add(cur);
                    }
                    cur = null;
                    curMsg = null;
                    if (tid.equals(header.group(3))) {
                        cur = new LinkedHashMap<>();
                        cur.put("time", header.group(1));
                        cur.put("thread", header.group(2));
                        cur.put("tid", header.group(3));
                        cur.put("level", header.group(4));
                        cur.put("logger", header.group(5));
                        cur.put("source", source);
                        curMsg = new StringBuilder(header.group(6));
                    }
                } else if (cur != null && curMsg != null) {
                    // 堆栈等续行归入上一条消息
                    curMsg.append('\n').append(line);
                }
            }
            if (cur != null && tid.equals(cur.get("tid"))) {
                cur.put("message", truncateMsg(curMsg.toString()));
                out.add(cur);
            }
        } catch (Exception e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("time", "1970-01-01 00:00:00.000");
            err.put("level", "WARN");
            err.put("logger", OpsService.class.getName());
            err.put("tid", tid);
            err.put("source", source);
            err.put("message", "日志文件读取失败: " + file + " - " + e.getMessage());
            out.add(err);
        }
        return out;
    }

    private String truncateMsg(String msg) {
        return msg.length() > MAX_ENTRY_MSG ? msg.substring(0, MAX_ENTRY_MSG) + "\n...(truncated)" : msg;
    }
}