package com.ihomy.common;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * 第三方服务出站调用统一封装(thirdparty 日志文件):
 * 出入各一条日志(>>> 请求 / <<< 响应 / !!! 异常),自动打服务名、脱敏 URL、
 * 耗时、状态码、响应摘要(截断 1KB);失败带完整堆栈。
 *
 * tid 取自 MDC(调用方请求的链路号),与 access/server 日志天然关联。
 * 后续新功能调三方 API 一律走这里,不要再手写 HttpURLConnection;
 * 自定义方法(PROPFIND 等)走 request(),GET 走 get() 委托。
 *
 * 百度网盘 dlink / WebDAV 大文件流式下载不适用(响应是 GB 级流,不能整包读成字符串),
 * 由调用方手动打 thirdparty 日志(StorageService.baiduOpen / WebDavClient.open 惯例)。
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
        return request(service, "GET", url, headers, null, timeoutMs);
    }

    /** 通用请求(GET/POST 等标准方法走 HttpURLConnection;PROPFIND 等自定义方法
     *  被 JDK HttpURLConnection 拒绝("Invalid HTTP method"),改走 java.net.http.HttpClient) */
    public static Resp request(String service, String method, String url, Map<String, String> headers,
                               byte[] body, int timeoutMs) throws IOException {
        var log = Loggers.thirdParty(service);
        String maskedUrl = maskUrl(url);
        long start = System.currentTimeMillis();
        log.info(">>> {} {} headers={} bodyLen={}", method, maskedUrl, describeHeaders(headers),
                body == null ? 0 : body.length);
        try {
            int status;
            String respBody;
            if (JDK_METHODS.contains(method)) {
                URL u = URI.create(url).toURL();
                HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                conn.setRequestMethod(method);
                conn.setConnectTimeout(timeoutMs);
                conn.setReadTimeout(timeoutMs);
                if (headers != null) {
                    headers.forEach(conn::setRequestProperty);
                }
                if (body != null && body.length > 0) {
                    conn.setDoOutput(true);
                    conn.setFixedLengthStreamingMode(body.length);
                    conn.getOutputStream().write(body);
                    conn.getOutputStream().close();
                }
                status = conn.getResponseCode();
                InputStream is = status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream();
                respBody = "";
                if (is != null) {
                    if ("gzip".equalsIgnoreCase(conn.getContentEncoding())) {
                        is = new GZIPInputStream(is);
                    }
                    respBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    is.close();
                }
                conn.disconnect();
            } else {
                HttpRequest.Builder rb = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofMillis(timeoutMs));
                if (headers != null) {
                    headers.forEach(rb::header);
                }
                rb.method(method, body != null && body.length > 0
                        ? HttpRequest.BodyPublishers.ofByteArray(body)
                        : HttpRequest.BodyPublishers.noBody());
                HttpResponse<byte[]> resp = HTTP_CLIENT.send(rb.build(), HttpResponse.BodyHandlers.ofByteArray());
                status = resp.statusCode();
                byte[] bytes = resp.body();
                if ("gzip".equalsIgnoreCase(resp.headers().firstValue("Content-Encoding").orElse(""))) {
                    bytes = gunzip(bytes);
                }
                respBody = new String(bytes, StandardCharsets.UTF_8);
            }
            log.info("<<< {} {} status={} costMs={} body={}", method, maskedUrl, status,
                    System.currentTimeMillis() - start, truncate(respBody));
            return new Resp(status, respBody);
        } catch (IOException e) {
            log.error("!!! {} {} failed costMs={}", method, maskedUrl, System.currentTimeMillis() - start, e);
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("!!! {} {} interrupted costMs={}", method, maskedUrl, System.currentTimeMillis() - start, e);
            throw new IOException("请求被中断", e);
        }
    }

    /** JDK HttpURLConnection 仅支持这些标准方法 */
    private static final Set<String> JDK_METHODS = Set.of("GET", "POST", "HEAD", "OPTIONS", "PUT", "DELETE", "TRACE");

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private static byte[] gunzip(byte[] data) throws IOException {
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(data))) {
            return in.readAllBytes();
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
