package com.ihomy.common;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * 第三方服务出站调用统一封装(thirdparty 日志文件):
 * 出入各一条日志(>>> 请求 / <<< 响应 / !!! 异常),自动打服务名、脱敏 URL、
 * 耗时、状态码、响应摘要(截断 1KB);失败带完整堆栈。
 *
 * tid 取自 MDC(调用方请求的链路号),与 access/server 日志天然关联。
 * 后续新功能调三方 API 一律走这里,不要再手写 HttpURLConnection。
 *
 * 百度网盘 dlink 大文件流式下载不适用(响应是 GB 级流,不能整包读成字符串),
 * 由 StorageService 手动打 thirdparty 日志。
 */
public final class ThirdPartyHttp {

    /** 响应:状态码 + 响应体字符串(非 2xx 也是错误流内容,便于排查) */
    public record Resp(int status, String body) {
        public boolean ok() {
            return status >= 200 && status < 300;
        }
    }

    /** URL query 敏感参数打码(token 类) */
    private static final Pattern SENSITIVE_QUERY =
            Pattern.compile("(?i)((?:token|access_token|refresh_token|secret|sign|key|password)=)[^&]*");
    /** 请求头敏感值打码 */
    private static final Pattern SENSITIVE_HEADER = Pattern.compile("(?i)^(authorization|.*token.*|.*secret.*)$");

    private static final int MAX_BODY_LOG = 1024;

    private ThirdPartyHttp() {
    }

    /** GET(自动处理 gzip);IO 失败抛 IOException 由调用方按业务语义兜底 */
    public static Resp get(String service, String url, Map<String, String> headers, int timeoutMs) throws IOException {
        var log = Loggers.thirdParty(service);
        String maskedUrl = maskUrl(url);
        long start = System.currentTimeMillis();
        log.info(">>> GET {} headers={}", maskedUrl, describeHeaders(headers));
        try {
            URL u = URI.create(url).toURL();
            HttpURLConnection conn = (HttpURLConnection) u.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            if (headers != null) {
                headers.forEach(conn::setRequestProperty);
            }
            int status = conn.getResponseCode();
            InputStream is = status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream();
            String body = "";
            if (is != null) {
                if ("gzip".equalsIgnoreCase(conn.getContentEncoding())) {
                    is = new GZIPInputStream(is);
                }
                body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                is.close();
            }
            conn.disconnect();
            log.info("<<< GET {} status={} costMs={} body={}", maskedUrl, status,
                    System.currentTimeMillis() - start, truncate(body));
            return new Resp(status, body);
        } catch (IOException e) {
            log.error("!!! GET {} failed costMs={}", maskedUrl, System.currentTimeMillis() - start, e);
            throw e;
        }
    }

    private static String maskUrl(String url) {
        int q = url.indexOf('?');
        if (q < 0) {
            return url;
        }
        return url.substring(0, q + 1) + SENSITIVE_QUERY.matcher(url.substring(q + 1)).replaceAll("$1***");
    }

    private static String describeHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return "-";
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (!first) sb.append(", ");
            first = false;
            sb.append(e.getKey()).append(": ")
                    .append(SENSITIVE_HEADER.matcher(e.getKey()).matches() ? "***" : e.getValue());
        }
        return sb.append('}').toString();
    }

    private static String truncate(String body) {
        return body != null && body.length() > MAX_BODY_LOG
                ? body.substring(0, MAX_BODY_LOG) + "...(truncated)" : body;
    }
}
