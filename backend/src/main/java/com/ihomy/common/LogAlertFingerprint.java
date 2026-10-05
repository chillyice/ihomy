package com.ihomy.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 日志「同类问题」归一化(纯函数,不依赖 Spring/logback,便于单测):
 *
 * 判定同一类问题的口径——
 *   - 带异常:级别 + logger + 异常类型 + 首个应用栈帧(类#方法)。故意不含异常消息与 URL 等易变内容,
 *     这样「一批不同组件的 Maven 探测超时」会归为同一类,而不是散成几十条各一次的噪声。
 *   - 无异常:级别 + logger + 归一化消息(数字/十六进制/UUID/追踪号一律压成 #,消除易变值)。
 *
 * 归一化后取 SHA-256 十六进制作为指纹 key(稳定、跨重启一致,可直接落库比较)。
 */
public final class LogAlertFingerprint {

    /** 一条待聚合的日志事件(appender 采集,不含任何 Spring 依赖) */
    public record Event(String level, String logger, String message,
                        String throwableClass, String throwableMessage,
                        String appFrame, String traceId) {
    }

    /** 聚合结果:key 为同类问题的稳定指纹,title 为可读摘要 */
    public record Fingerprint(String key, String title) {
    }

    private static final Pattern UUID_RE =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern HEX_RE = Pattern.compile("\\b[0-9a-fA-F]{8,}\\b");
    private static final Pattern DIGIT_RE = Pattern.compile("\\d+");
    private static final Pattern SPACE_RE = Pattern.compile("\\s+");

    /** 消息归一化上限(超过部分截断,避免超长 SQL/JSON 撑大指纹) */
    private static final int NORM_MAX = 200;
    /** 摘要上限 */
    private static final int TITLE_MAX = 220;
    private static final String UNKNOWN = "(未识别日志)";

    private LogAlertFingerprint() {
    }

    public static Fingerprint of(Event e) {
        String level = blankTo(e.level(), "INFO");
        String logger = blankTo(e.logger(), "-");
        if (e.throwableClass() != null && !e.throwableClass().isBlank()) {
            String frame = e.appFrame() == null ? "" : e.appFrame();
            String key = hash(level + "|" + logger + "|" + e.throwableClass() + "|" + frame);
            StringBuilder title = new StringBuilder(shortName(e.throwableClass()));
            String msg = oneLine(e.throwableMessage());
            if (!msg.isEmpty()) {
                title.append(": ").append(msg);
            }
            if (!frame.isEmpty()) {
                title.append(" @ ").append(frameShort(frame));
            }
            return new Fingerprint(key, truncate(title.toString(), TITLE_MAX));
        }
        String norm = normalize(e.message());
        if (norm.isEmpty()) {
            norm = UNKNOWN;
        }
        return new Fingerprint(hash(level + "|" + logger + "|" + norm), truncate(norm, TITLE_MAX));
    }

    /** 归一化消息:压平空白 → 抹掉 UUID/长十六进制/数字串(时间、耗时、ID、tid 等易变值) */
    public static String normalize(String message) {
        if (message == null) {
            return "";
        }
        String s = oneLine(message);
        if (s.isEmpty()) {
            return "";
        }
        s = UUID_RE.matcher(s).replaceAll("#");
        s = HEX_RE.matcher(s).replaceAll("#");
        s = DIGIT_RE.matcher(s).replaceAll("#");
        return truncate(s, NORM_MAX);
    }

    /** 取首行并压平连续空白(堆栈、多行消息只留首行做指纹) */
    private static String oneLine(String s) {
        if (s == null) {
            return "";
        }
        int nl = s.indexOf('\n');
        String first = nl < 0 ? s : s.substring(0, nl);
        return SPACE_RE.matcher(first).replaceAll(" ").trim();
    }

    /** 异常类名取简名:java.net.SocketTimeoutException → SocketTimeoutException */
    public static String shortName(String className) {
        int dot = className.lastIndexOf('.');
        return dot < 0 ? className : className.substring(dot + 1);
    }

    /** 栈帧简写:com.ihomy.common.ThirdPartyHttp#request → ThirdPartyHttp#request */
    public static String frameShort(String frame) {
        if (frame == null || frame.isEmpty()) {
            return "";
        }
        int hash = frame.indexOf('#');
        String cls = hash < 0 ? frame : frame.substring(0, hash);
        int dot = cls.lastIndexOf('.');
        return (dot < 0 ? cls : cls.substring(dot + 1)) + (hash < 0 ? "" : frame.substring(hash));
    }

    private static String hash(String source) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] out = md.digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(out.length * 2);
            for (byte b : out) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString().toLowerCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 必备算法,不会走到;退化用来源字符串本身保证仍能分组
            return Integer.toHexString(source.hashCode());
        }
    }

    private static String blankTo(String v, String fallback) {
        return v == null || v.isBlank() ? fallback : v;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
