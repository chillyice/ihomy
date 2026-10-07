package com.ihomy.common;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
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

    /**
     * 是否落在私有/回环/链路本地/组播网段:抓取用户给的外部地址(如图片直链)时用,
     * 与 isBlockedHost 互补——后者按主机名字符串拦,这里按解析结果拦(域名可指向内网)。
     * 注意家庭自建服务地址(HA/本地模型)走 normalize() 不做此检查,别混用。
     * ponytail:解析与建连之间仍有 DNS 重绑定窗口,家庭场景可接受
     */
    public static boolean isPrivateAddress(InetAddress addr) {
        if (addr.isAnyLocalAddress() || addr.isLoopbackAddress() || addr.isLinkLocalAddress()
                || addr.isSiteLocalAddress() || addr.isMulticastAddress()) {
            return true;
        }
        byte[] b = addr.getAddress();
        if (b.length == 4) {
            // 100.64.0.0/10 共享地址段:云厂商内网与运营商级 NAT(阿里云元数据 100.100.100.200 在此段)
            return b[0] == 100 && (b[1] & 0xC0) == 0x40;
        }
        if (b.length != 16) return false;
        if ((b[0] & 0xFE) == 0xFC) return true; // fc00::/7 IPv6 唯一本地地址
        boolean v4mapped = true;
        for (int i = 0; i < 10; i++) {
            if (b[i] != 0) { v4mapped = false; break; }
        }
        if (v4mapped && b[10] == (byte) 0xFF && b[11] == (byte) 0xFF) {
            try {
                // ::ffff:a.b.c.d 映射地址取内嵌 IPv4 再判(192.168.1.1 可这样绕前缀)
                return isPrivateAddress(InetAddress.getByAddress(new byte[]{b[12], b[13], b[14], b[15]}));
            } catch (UnknownHostException e) {
                return true; // 4 字节数组恒合法,不会发生;保守判私有
            }
        }
        return false;
    }
}
