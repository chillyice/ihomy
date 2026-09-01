package com.ihomy.filter;

import com.ihomy.common.Ips;
import com.ihomy.common.Loggers;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 接口访问日志过滤器(access 日志文件):每个客户端请求一条完整记录——
 * 方法/URI(含 query)/操作人/IP/HTTP 状态/业务 code/耗时/入参/响应摘要。
 *
 * - 入参捕获上限 4KB、响应 2KB(流式旁路复制,大文件上传下载零额外内存)
 * - password/token/captcha 等敏感字段打码
 * - 慢接口(>3s)或失败(HTTP>=400/业务 code!=0)升 WARN,便于 grep 定位问题请求
 * - WebSocket 握手只记请求行不包装流(包装会破坏 Tomcat 协议升级);
 *   WS 消息级日志由 ChatWebSocketHandler 补记
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AccessLogFilter extends OncePerRequestFilter {

    private static final Logger accessLog = Loggers.access();

    private static final int MAX_REQ_CAPTURE = 4096;
    private static final int MAX_RESP_CAPTURE = 2048;
    private static final int MAX_LOG_FIELD = 2048;
    private static final long SLOW_MS = 3000;

    /** JSON 入参/响应敏感字段打码(值替换为 ***) */
    private static final Pattern SENSITIVE_JSON =
            Pattern.compile("(?i)(\"(?:password|passwd|oldPassword|newPassword|token|accessToken|access_token"
                    + "|refreshToken|refresh_token|secret|secretKey|privateKey|captcha|captchaCode|authorization)"
                    + "\"\\s*:\\s*\")([^\"]*)(\")");
    /** query 敏感参数打码 */
    private static final Pattern SENSITIVE_QUERY =
            Pattern.compile("(?i)((?:password|token|access_token|refresh_token|secret|captcha)=)[^&]*");
    /** 统一响应体首个 code 字段(判定业务成败) */
    private static final Pattern RESP_CODE = Pattern.compile("\"code\"\\s*:\\s*(\\d+)");
    /** 统一响应体首个 message 字段(大响应摘要用) */
    private static final Pattern RESP_MESSAGE = Pattern.compile("\"message\"\\s*:\\s*\"([^\"]*)\"");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean ws = isWebSocketUpgrade(request);
        // multipart/octet-stream 不捕获请求体(大二进制无日志价值)
        boolean captureReq = !ws && isTextBody(request.getContentType());
        CaptureRequestWrapper reqW = captureReq ? new CaptureRequestWrapper(request, MAX_REQ_CAPTURE) : null;
        CaptureResponseWrapper resW = !ws ? new CaptureResponseWrapper(response, MAX_RESP_CAPTURE) : null;
        HttpServletRequest reqToUse = reqW != null ? reqW : request;
        HttpServletResponse resToUse = resW != null ? resW : response;

        long start = System.currentTimeMillis();
        try {
            chain.doFilter(reqToUse, resToUse);
        } finally {
            logAccess(reqToUse, resToUse, reqW, resW, System.currentTimeMillis() - start);
        }
    }

    private void logAccess(HttpServletRequest req, HttpServletResponse res,
                           CaptureRequestWrapper reqW, CaptureResponseWrapper resW, long cost) {
        try {
            String uri = req.getRequestURI()
                    + (req.getQueryString() == null ? "" : "?" + maskQuery(req.getQueryString()));
            String user = userOf(req);
            int status = res.getStatus();
            Integer bizCode = null;
            String resp = null;
            String type = res.getContentType();
            if (resW != null && type != null && (type.contains("json") || type.contains("text"))) {
                String captured = maskJson(resW.captured());
                var m = RESP_CODE.matcher(captured);
                if (m.find()) {
                    bizCode = Integer.valueOf(m.group(1));
                }
                // 小响应(<=512 字符)整体记录;大响应只记 code+message,防止列表类接口刷屏
                resp = captured.length() <= 512 ? captured : respSummary(captured);
            }
            boolean slow = cost > SLOW_MS;
            boolean fail = status >= 400 || (bizCode != null && bizCode != 0);

            String msg = req.getMethod() + " " + uri
                    + " user=" + user
                    + " ip=" + Ips.realIp(req)
                    + " status=" + status
                    + " code=" + (bizCode == null ? "-" : bizCode)
                    + " cost=" + cost + "ms"
                    + " req=" + requestBody(req, reqW)
                    + (resp == null || resp.isEmpty() ? "" : " resp=" + truncate(resp, MAX_LOG_FIELD));
            if (slow || fail) {
                accessLog.warn(msg);
            } else {
                accessLog.info(msg);
            }
        } catch (Exception e) {
            // 访问日志自身异常绝不能影响业务
            accessLog.warn("access log record failed: {}", e.getMessage());
        }
    }

    /** 请求体描述:JSON/文本=捕获内容;表单=参数表;multipart=提示;其余=略 */
    private String requestBody(HttpServletRequest req, CaptureRequestWrapper reqW) {
        String type = req.getContentType();
        if (type == null) {
            return "-";
        }
        if (type.contains("application/x-www-form-urlencoded")) {
            StringBuilder sb = new StringBuilder("{");
            Map<String, String[]> params = req.getParameterMap();
            boolean first = true;
            for (Map.Entry<String, String[]> e : params.entrySet()) {
                if (!first) sb.append(", ");
                first = false;
                sb.append('"').append(e.getKey()).append("\":\"")
                        .append(SENSITIVE_JSON.matcher("\"" + e.getKey() + "\":\"x\"").matches()
                                ? "***" : String.join(",", e.getValue()))
                        .append('"');
            }
            return sb.append('}').toString();
        }
        if (type.contains("multipart")) {
            return "(multipart len=" + req.getContentLengthLong() + ")";
        }
        if (reqW != null) {
            String body = reqW.captured();
            return body.isEmpty() ? "-" : maskJson(body);
        }
        return "(binary)";
    }

    private String userOf(HttpServletRequest req) {
        Object id = req.getAttribute("ihomy.userId");
        Object name = req.getAttribute("ihomy.username");
        if (id == null) {
            return "anonymous";
        }
        return name + "#" + id;
    }

    private boolean isWebSocketUpgrade(HttpServletRequest req) {
        return "websocket".equalsIgnoreCase(req.getHeader("Upgrade"));
    }

    /** JSON / 表单 / 文本才捕获请求体 */
    private boolean isTextBody(String contentType) {
        if (contentType == null) {
            return false;
        }
        String t = contentType.toLowerCase();
        return t.contains("json") || t.contains("x-www-form-urlencoded") || t.contains("text/");
    }

    private String maskJson(String body) {
        return SENSITIVE_JSON.matcher(body).replaceAll("$1***$3");
    }

    /** 大响应摘要:只取统一结构里的 code + message 字段 */
    private String respSummary(String json) {
        String code = "-", message = "-";
        var mc = RESP_CODE.matcher(json);
        if (mc.find()) {
            code = mc.group(1);
        }
        var mm = RESP_MESSAGE.matcher(json);
        if (mm.find()) {
            message = mm.group(1);
        }
        return "{\"code\":" + code + ",\"message\":\"" + message + "\",...(data omitted)}";
    }

    private String maskQuery(String query) {
        return SENSITIVE_QUERY.matcher(query).replaceAll("$1***");
    }

    private String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "...(truncated)" : s;
    }
}
