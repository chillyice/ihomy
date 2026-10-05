package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ihomy.common.LogAlertAppender;
import com.ihomy.common.LogAlertFingerprint;
import com.ihomy.entity.ReportAlert;
import com.ihomy.mapper.ReportAlertMapper;
import com.ihomy.mapper.SysRoleMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 同类异常聚合预警(V10.15):
 *
 * 日志里的「同类问题」在统计窗口内出现次数达到阈值时,汇总成一条 report_alert 落库,
 * 并在运维页「异常预警」里提醒 OPS(配了 SMTP 时同时发一封邮件)。
 * 目的:把「同一个原因反复刷屏」的报错(如第三方探测连续读超时)从海量日志里提取出来,
 * 让运维一眼看到"哪一类问题、多久内出现了多少次、最近一次是什么时候"。
 *
 * 采集端是 {@link LogAlertAppender}(logback appender),本类作为其 sink:
 * 计数在内存里按指纹+窗口做,达到阈值只入队;落库/发信由单条后台线程完成,
 * 不在业务线程上做任何 IO。指纹上限(max-fingerprints)兜底内存,超限不再新增跟踪项。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LogAlertService implements LogAlertAppender.Sink {

    private final ReportAlertMapper alertMapper;
    private final SysRoleMapper sysRoleMapper;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${app.log-alert.enabled:true}")
    private boolean enabled;
    /** 参与聚合的日志级别(逗号分隔);含 WARN 会连访问慢请求一起统计 */
    @Value("${app.log-alert.levels:ERROR}")
    private String levelsCsv;
    /** 统计窗口(秒):窗口内同一指纹累加,到点重新计数 */
    @Value("${app.log-alert.window-seconds:600}")
    private int windowSeconds;
    /** 阈值:窗口内同一指纹达到该次数即预警 */
    @Value("${app.log-alert.threshold:10}")
    private int threshold;
    /** 冷却(秒):同一指纹两次预警的最短间隔,避免刚报完又刷一条 */
    @Value("${app.log-alert.cooldown-seconds:1800}")
    private int cooldownSeconds;
    /** 内存中同时跟踪的指纹上限(防高基数消息撑爆堆内存) */
    @Value("${app.log-alert.max-fingerprints:200}")
    private int maxFingerprints;
    /** 不参与聚合的 logger(逗号分隔),默认排除预警自身 */
    @Value("${app.log-alert.exclude-loggers:com.ihomy.service.LogAlertService}")
    private String excludeLoggersCsv;
    /** 预警记录保留天数 */
    @Value("${app.log-alert.retention-days:30}")
    private int retentionDays;
    /** 是否在配好邮件通道时向 OPS 邮箱发提醒 */
    @Value("${app.log-alert.mail-enabled:true}")
    private boolean mailEnabled;
    @Value("${spring.mail.host:}")
    private String mailHost;
    @Value("${spring.mail.username:}")
    private String mailUsername;
    @Value("${app.mail.from:}")
    private String mailFrom;

    private Set<String> levelSet = Set.of("ERROR");
    private Set<String> excludeSet = Set.of();

    /** 指纹 → 当前窗口计数 */
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    /** 待落库的预警(有界,满则丢弃并计数,绝不阻塞日志线程) */
    private final BlockingQueue<ReportAlert> pending = new ArrayBlockingQueue<>(512);
    private final AtomicLong dropped = new AtomicLong();

    private volatile boolean running;
    private Thread consumer;

    /** 窗口计数(按指纹各自加锁,避免全局锁) */
    private static final class Window {
        long startMs;
        int count;
        long lastAlertMs;
        String sampleMessage;
        String sampleTraceId;
        LocalDateTime firstSeen;
        LocalDateTime lastSeen;
    }

    @PostConstruct
    void start() {
        levelSet = split(levelsCsv);
        if (levelSet.isEmpty()) {
            levelSet = Set.of("ERROR");
        }
        excludeSet = split(excludeLoggersCsv);
        if (!enabled || threshold < 1 || windowSeconds < 1) {
            log.info("同类异常聚合预警未启用(enabled={}, threshold={}, windowSeconds={})", enabled, threshold, windowSeconds);
            return;
        }
        running = true;
        consumer = new Thread(this::consumeLoop, LogAlertAppender.CONSUMER_THREAD_PREFIX + "-consumer");
        consumer.setDaemon(true);
        consumer.start();
        LogAlertAppender.setSink(this);
        log.info("同类异常聚合预警已启用: levels={} window={}s threshold={} cooldown={}s maxFingerprints={}",
                levelSet, windowSeconds, threshold, cooldownSeconds, maxFingerprints);
    }

    @PreDestroy
    void stop() {
        LogAlertAppender.setSink(null);
        running = false;
        if (consumer != null) {
            consumer.interrupt();
        }
    }

    // ---------------- 采集端 ----------------

    @Override
    public void onEvent(LogAlertFingerprint.Event event) {
        if (!running || event == null) {
            return;
        }
        if (event.level() == null || !levelSet.contains(event.level().toUpperCase())) {
            return;
        }
        if (event.logger() != null && excludeSet.contains(event.logger())) {
            return;
        }
        LogAlertFingerprint.Fingerprint fp = LogAlertFingerprint.of(event);
        Window w = windows.get(fp.key());
        if (w == null) {
            if (windows.size() >= maxFingerprints) {
                // 已达跟踪上限:不再新增指纹,防止异常消息含高基数内容时堆内存被撑爆
                dropped.incrementAndGet();
                return;
            }
            w = windows.computeIfAbsent(fp.key(), k -> new Window());
        }
        ReportAlert fired = null;
        long now = System.currentTimeMillis();
        synchronized (w) {
            if (w.startMs == 0 || now - w.startMs >= windowSeconds * 1000L) {
                reset(w, now);
            }
            w.count++;
            w.lastSeen = LocalDateTime.now();
            if (w.sampleMessage == null) {
                w.sampleMessage = event.message();
                w.sampleTraceId = event.traceId();
            }
            if (w.count >= threshold && (w.lastAlertMs == 0 || now - w.lastAlertMs >= cooldownSeconds * 1000L)) {
                fired = build(fp, event, w);
                // 触发后开新窗口(冷却期内继续累计但不重复报),冷却结束后若仍在刷屏会再次预警
                w.lastAlertMs = now;
                reset(w, now);
            }
        }
        if (fired != null && !pending.offer(fired)) {
            dropped.incrementAndGet();
            log.warn("异常预警队列已满,丢弃一条: {}", fired.getTitle());
        }
    }

    private void reset(Window w, long nowMs) {
        w.startMs = nowMs;
        w.count = 0;
        w.sampleMessage = null;
        w.sampleTraceId = null;
        w.firstSeen = LocalDateTime.now();
    }

    private ReportAlert build(LogAlertFingerprint.Fingerprint fp, LogAlertFingerprint.Event e, Window w) {
        ReportAlert a = new ReportAlert();
        a.setFingerprint(fp.key());
        a.setLevel(e.level() == null ? "ERROR" : e.level().toUpperCase());
        a.setLogger(truncate(e.logger(), 128));
        a.setTitle(truncate(fp.title(), 255));
        a.setSampleMessage(truncate(e.message(), 1000));
        a.setSampleTraceId(truncate(e.traceId(), 64));
        a.setOccurrenceCount(w.count);
        a.setWindowSeconds(windowSeconds);
        a.setThreshold(threshold);
        a.setFirstSeen(w.firstSeen);
        a.setLastSeen(w.lastSeen);
        a.setStatus("OPEN");
        a.setNotified(0);
        a.setCreatedAt(LocalDateTime.now());
        return a;
    }

    /** 单条后台消费线程:落库 + 邮件提醒(异常只记录,不影响后续) */
    private void consumeLoop() {
        while (running) {
            try {
                ReportAlert a = pending.poll(1, TimeUnit.SECONDS);
                if (a == null) {
                    continue;
                }
                alertMapper.insert(a);
                if (dropped.get() > 0) {
                    log.warn("异常预警累计丢弃 {} 条(队列满或跟踪指纹达上限)", dropped.getAndSet(0));
                }
                sendMail(a);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception ex) {
                log.warn("异常预警落库失败: {}", ex.getMessage());
            }
        }
    }

    private void sendMail(ReportAlert a) {
        if (!mailEnabled || !StringUtils.hasText(mailHost)) {
            return;
        }
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            return;
        }
        List<String> to = sysRoleMapper.selectOpsEmails();
        if (to == null || to.isEmpty()) {
            return;
        }
        try {
            MimeMessageHelper helper = new MimeMessageHelper(sender.createMimeMessage(), false, "UTF-8");
            helper.setTo(to.toArray(new String[0]));
            helper.setSubject("[ihomy] 同类异常预警: " + a.getTitle());
            String from = StringUtils.hasText(mailFrom) ? mailFrom : mailUsername;
            if (StringUtils.hasText(from)) {
                helper.setFrom(from);
            }
            helper.setText(mailBody(a), true);
            sender.send(helper.getMimeMessage());
            alertMapper.update(null, new LambdaUpdateWrapper<ReportAlert>()
                    .eq(ReportAlert::getId, a.getId())
                    .set(ReportAlert::getNotified, 1));
        } catch (Exception ex) {
            log.warn("异常预警邮件发送失败: {}", ex.getMessage());
        }
    }

    private String mailBody(ReportAlert a) {
        return """
                <div style="font-family:sans-serif;line-height:1.8;color:#3a2e22">
                  <p>系统在短时间内反复出现同一类问题，已汇总如下，请到 ihomy 运维页「异常预警」查看并处理。</p>
                  <ul>
                    <li>问题：%s</li>
                    <li>级别：%s</li>
                    <li>来源：%s</li>
                    <li>出现次数：%d 次 / %d 秒</li>
                    <li>首次：%s，最近：%s</li>
                  </ul>
                  <p style="color:#8a7a6a">日志摘录：</p>
                  <pre style="white-space:pre-wrap;background:#f6f2ec;padding:10px;border-radius:8px">%s</pre>
                </div>
                """.formatted(a.getTitle(), a.getLevel(), a.getLogger(),
                a.getOccurrenceCount(), a.getWindowSeconds(),
                a.getFirstSeen(), a.getLastSeen(), escapeHtml(truncate(a.getSampleMessage(), 2000)));
    }

    // ---------------- 运维侧查询/处理 ----------------

    /** 预警分页(status 可选 OPEN/ACKED,level 可选 ERROR/WARN),按最近出现时间倒序 */
    public IPage<ReportAlert> page(int current, int size, String status, String level) {
        LambdaQueryWrapper<ReportAlert> qw = new LambdaQueryWrapper<>();
        qw.eq(StringUtils.hasText(status), ReportAlert::getStatus, status)
                .eq(StringUtils.hasText(level), ReportAlert::getLevel, level)
                .orderByDesc(ReportAlert::getLastSeen)
                .orderByDesc(ReportAlert::getId);
        return alertMapper.selectPage(new Page<>(current, Math.min(Math.max(size, 1), 200)), qw);
    }

    /** 汇总:待处理/今日新增/累计/待处理中的 ERROR 数(运维页角标用) */
    public Map<String, Object> summary() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("open", count("OPEN", null, null));
        m.put("openError", count("OPEN", "ERROR", null));
        m.put("today", count(null, null, LocalDate.now().atStartOfDay()));
        m.put("total", count(null, null, null));
        return m;
    }

    private long count(String status, String level, LocalDateTime createdFrom) {
        LambdaQueryWrapper<ReportAlert> qw = new LambdaQueryWrapper<>();
        qw.eq(StringUtils.hasText(status), ReportAlert::getStatus, status)
                .eq(StringUtils.hasText(level), ReportAlert::getLevel, level)
                .ge(createdFrom != null, ReportAlert::getCreatedAt, createdFrom);
        return alertMapper.selectCount(qw);
    }

    /** 标记单条已处理 */
    public boolean ack(Long id, Long operatorId) {
        if (id == null) {
            return false;
        }
        LambdaUpdateWrapper<ReportAlert> uw = new LambdaUpdateWrapper<>();
        uw.eq(ReportAlert::getId, id)
                .eq(ReportAlert::getStatus, "OPEN")
                .set(ReportAlert::getStatus, "ACKED")
                .set(ReportAlert::getAckedBy, operatorId)
                .set(ReportAlert::getAckedAt, LocalDateTime.now());
        return alertMapper.update(null, uw) > 0;
    }

    /** 一键把当前所有待处理标记为已处理,返回处理条数 */
    public int ackAll(Long operatorId) {
        LambdaUpdateWrapper<ReportAlert> uw = new LambdaUpdateWrapper<>();
        uw.eq(ReportAlert::getStatus, "OPEN")
                .set(ReportAlert::getStatus, "ACKED")
                .set(ReportAlert::getAckedBy, operatorId)
                .set(ReportAlert::getAckedAt, LocalDateTime.now());
        return alertMapper.update(null, uw);
    }

    /** 超期预警清理(默认保留 30 天) */
    @Scheduled(cron = "0 20 4 * * *")
    public void purgeExpired() {
        if (retentionDays < 1) {
            return;
        }
        try {
            int n = alertMapper.delete(new LambdaQueryWrapper<ReportAlert>()
                    .lt(ReportAlert::getCreatedAt, LocalDate.now().minusDays(retentionDays).atStartOfDay()));
            if (n > 0) {
                log.info("清理超期异常预警 {} 条(保留 {} 天)", n, retentionDays);
            }
        } catch (Exception e) {
            log.warn("清理超期异常预警失败: {}", e.getMessage());
        }
    }

    /** 回收长期未再出现的窗口计数,保持内存里的跟踪项精简 */
    @Scheduled(fixedDelay = 600_000, initialDelay = 600_000)
    public void sweepWindows() {
        if (windows.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        long idleMs = windowSeconds * 2000L;
        windows.entrySet().removeIf(en -> {
            Window w = en.getValue();
            synchronized (w) {
                return now - w.startMs >= idleMs && now - w.lastAlertMs >= idleMs;
            }
        });
    }

    private static Set<String> split(String csv) {
        if (!StringUtils.hasText(csv)) {
            return Set.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toUpperCase())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
