package top.ihomy.app.net;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 原生侧最小 HTTP 封装(零依赖):桌面卡片同步与每日天气共用,避免两份 HttpURLConnection 代码各写一遍。
 *
 * <p>只做 GET 与无请求体 POST,鉴权一律 Bearer 头;非 2xx 抛 {@link IllegalStateException},
 * 由调用方决定「跳过本轮」还是「交给 WorkManager 重试」——这里不做任何策略判断。
 */
public final class SimpleHttp {

    private static final int CONNECT_TIMEOUT = 8000;
    /** 天气详情是服务端一次聚合多个上游(逐日/逐小时/空气/预警),读超时给宽一点 */
    private static final int READ_TIMEOUT = 20000;

    private SimpleHttp() {
    }

    /** GET 并返回响应体(UTF-8);2xx 之外抛异常 */
    public static String get(String url, String token) throws Exception {
        return request(url, token, "GET");
    }

    /** 无请求体 POST;2xx 即成功(沿用卡片勾选的用法,不需要响应体) */
    public static void post(String url, String token) throws Exception {
        request(url, token, "POST");
    }

    private static String request(String url, String token, String method) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setRequestMethod(method);
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setRequestProperty("Accept", "application/json");
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
            if ("POST".equals(method)) return "";
            InputStream in = conn.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            in.close();
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            conn.disconnect();
        }
    }
}
