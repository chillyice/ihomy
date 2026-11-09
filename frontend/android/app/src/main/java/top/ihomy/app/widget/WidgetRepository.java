package top.ihomy.app.widget;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 卡片数据仓库:凭 WIDGET 令牌 GET /schedule/occurrences,把三张卡片需要的字段解析成快照缓存。
 * 网络栈用 HttpURLConnection(零依赖);日期算法零实现,服务端 occurrences 是唯一事实(计划 §6.4.1)。
 * 服务端地址取构建期注入的 API 基址(capacitor config 的 server.url 不适用原生侧,直接读 BuildConfig 外的常量类 ApiBase)。
 */
public final class WidgetRepository {

    private static final String TAG = "IhomyWidget";
    /** 拉取窗口:今天 → +29 天(与前端注册闹钟窗口一致) */
    private static final int WINDOW_DAYS = 30;

    private WidgetRepository() {
    }

    /** API 基址(如 https://ihomy.top/api);由 ApiBaseGenerator 在构建期生成,包名隔离避免与 Web 产物冲突 */
    public static String apiBase() {
        return top.ihomy.app.ApiBase.URL;
    }

    public static class Occurrence {
        public String key;
        public String kind;
        public String title;
        public String date;
        public String time;
        public long days = Long.MIN_VALUE;
    }

    /** 拉取并解析;抛异常时调用方保持旧缓存不动 */
    public static List<Occurrence> fetchOccurrences(Context context) throws Exception {
        String token = WidgetPrefs.token(context);
        if (token == null || token.isEmpty()) throw new IllegalStateException("no widget token");
        LocalDate from = LocalDate.now();
        LocalDate to = from.plusDays(WINDOW_DAYS - 1L);
        String url = apiBase() + "/schedule/occurrences?from=" + from + "&to=" + to;
        String body = get(url, token);
        JSONObject root = new JSONObject(body);
        if (root.optInt("code", -1) != 0) throw new IllegalStateException("bad code " + root.optInt("code"));
        JSONArray items = root.getJSONObject("data").getJSONArray("items");
        List<Occurrence> out = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject it = items.getJSONObject(i);
            Occurrence o = new Occurrence();
            o.key = it.optString("key", "");
            o.kind = it.optString("kind", "");
            o.title = it.optString("title", "");
            o.date = it.optString("date", "");
            o.time = it.isNull("time") ? null : it.optString("time", null);
            o.days = it.isNull("days") ? Long.MIN_VALUE : it.optLong("days", Long.MIN_VALUE);
            out.add(o);
        }
        return out;
    }

    /** 缓存结构:{"items":[...]}——原样存解析后的最小字段,渲染时不再依赖网络 */
    public static void cacheItems(Context context, String widgetId, List<Occurrence> items) {
        try {
            JSONArray arr = new JSONArray();
            for (Occurrence o : items) {
                JSONObject it = new JSONObject();
                it.put("key", o.key);
                it.put("kind", o.kind);
                it.put("title", o.title);
                it.put("date", o.date);
                it.put("time", o.time == null ? JSONObject.NULL : o.time);
                if (o.days != Long.MIN_VALUE) it.put("days", o.days);
                arr.put(it);
            }
            JSONObject wrap = new JSONObject();
            wrap.put("items", arr);
            WidgetPrefs.putCache(context, widgetId, wrap.toString());
        } catch (Exception e) {
            Log.w(TAG, "cache write failed", e);
        }
    }

    public static List<Occurrence> readCache(Context context, String widgetId) {
        List<Occurrence> out = new ArrayList<>();
        try {
            String raw = WidgetPrefs.cache(context, widgetId);
            if (raw == null || raw.isEmpty()) return out;
            JSONArray arr = new JSONObject(raw).getJSONArray("items");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject it = arr.getJSONObject(i);
                Occurrence o = new Occurrence();
                o.key = it.optString("key", "");
                o.kind = it.optString("kind", "");
                o.title = it.optString("title", "");
                o.date = it.optString("date", "");
                o.time = it.isNull("time") ? null : it.optString("time", null);
                o.days = it.isNull("days") ? Long.MIN_VALUE : it.optLong("days", Long.MIN_VALUE);
                out.add(o);
            }
        } catch (Exception e) {
            Log.w(TAG, "cache read failed", e);
        }
        return out;
    }

    private static String get(String url, String token) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(15000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setRequestProperty("Accept", "application/json");
            int code = conn.getResponseCode();
            if (code != 200) throw new IllegalStateException("HTTP " + code);
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
