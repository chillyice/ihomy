package com.ihomy.common;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端真实 IP 解析:依次取代理头,取不到回退 remoteAddr。
 * AccessLogFilter 与 OperationLogAspect 共用。
 */
public final class Ips {

    private Ips() {
    }

    public static String realIp(HttpServletRequest req) {
        String ip = req.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = req.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = req.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = req.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
