package com.ihomy.common;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * 外部服务地址规范化 + 目标限制(SSRF 基本防线):
 * 只放行 http/https、去尾斜杠,并拒绝云元数据端点/链路本地地址/十进制与十六进制 IP 写法。
 * 家庭内网地址(127.0.0.1/192.168.x)对家庭自建服务是刚需,不在拦截范围。
 */
public final class HttpUrlUtil {

    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "169.254.169.254", "metadata.google.internal", "metadata.goog", "100.100.100.200",
            "192.0.0.192", "fd00:ec2::254", "instance-data");

    private HttpUrlUtil() {
    }

    /** 规范化地址:无协议补 http://,去尾斜杠;不合法/被拦截时抛业务异常(消息由调用方给) */
    public static String normalize(String raw, String errorMessage) {
        return normalize(raw, errorMessage, "这个地址不能作为服务地址,请填家里能访问到的地址");
    }

    /** 同上,拦截目标时用调用方自己的提示语 */
    public static String normalize(String raw, String errorMessage, String blockedMessage) {
        if (raw == null || raw.isBlank()) throw new BizException(ResultCode.BAD_REQUEST, errorMessage);
        String v = raw.trim();
        String lower = v.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            // 带协议但不是 http(s) 的直接判非法,否则会补成 http://ftp://… 这种怪地址
            if (lower.contains("://")) throw new BizException(ResultCode.BAD_REQUEST, errorMessage);
            v = "http://" + v;
        }
        String bare = v.endsWith("/") ? v.substring(0, v.length() - 1) : v;
        URI uri;
        try {
            uri = URI.create(bare);
            if (uri.getHost() == null || uri.getScheme() == null) throw new IllegalArgumentException("no host");
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException("bad scheme");
            }
        } catch (IllegalArgumentException e) {
            throw new BizException(ResultCode.BAD_REQUEST, errorMessage);
        }
        if (isBlockedHost(uri.getHost())) {
            throw new BizException(ResultCode.BAD_REQUEST, blockedMessage);
        }
        return bare;
    }

    /** 云元数据端点/链路本地地址:读得到凭证的那些一律拒绝 */
    public static boolean isBlockedHost(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        if (h.startsWith("[") && h.endsWith("]")) h = h.substring(1, h.length() - 1);
        if (BLOCKED_HOSTS.contains(h) || "0.0.0.0".equals(h)) return true;
        if (h.startsWith("169.254.") || h.startsWith("fe80:")) return true;
        // 十进制/十六进制形式的 IP(169.254.169.254 可写成 2852039166 或 0xA9FEA9FE),当非法地址
        if (h.matches("^\\d+$") || h.matches("^0x[0-9a-f]+$")) return true;
        // IPv4 映射 IPv6(::ffff:169.254.169.254)绕过上面的前缀判断,取出内嵌 IPv4 再判一次
        int mapped = h.lastIndexOf(":ffff:");
        return mapped >= 0 && isBlockedHost(h.substring(mapped + 6));
    }
}
