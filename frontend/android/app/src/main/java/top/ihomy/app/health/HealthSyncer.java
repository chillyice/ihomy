package top.ihomy.app.health;

import android.content.Context;

import org.json.JSONObject;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import top.ihomy.app.ApiBase;
import top.ihomy.app.net.SimpleHttp;

/**
 * 健康数据同步内核:读 Health Connect → 按 ≤7 天切窗 → POST /health/ingest。
 * 前台「立即同步」(插件)与每日后台任务(WorkManager)共用这一份,避免两套窗口/重试口径。
 *
 * 增量口径:上次成功同步时刻往前多取 1 小时重发(服务端按整窗删插,重发幂等),
 * 首次没记录时回填 days 天(默认 90,上限 90);不用 changesToken——服务端不接受增量补丁。
 */
public final class HealthSyncer {

    /** 单次请求窗口:7 天以内保证「心率小时桶 + 日聚合 + 睡眠区间」远低于后端 2000 条/请求 */
    private static final int CHUNK_DAYS = 7;
    private static final int MAX_BACKFILL_DAYS = 90;

    private HealthSyncer() {
    }

    /** 同步结果:{accepted, duplicated, rejected, skipped, requests, from, to} */
    public static JSONObject run(Context context, String token, List<String> groups, int days) throws Exception {
        if (token == null || token.trim().isEmpty()) throw new IllegalStateException("未配置上传令牌");
        if (groups == null || groups.isEmpty()) throw new IllegalStateException("尚未授权任何权限分组");

        Instant now = Instant.now();
        int backfill = Math.max(1, Math.min(days, MAX_BACKFILL_DAYS));
        Instant earliest = now.minus(Duration.ofDays(backfill));
        long lastAt = HealthPrefs.lastSyncAt(context);
        Instant from = lastAt > 0 ? Instant.ofEpochMilli(lastAt).minus(Duration.ofHours(1)) : earliest;
        if (from.isBefore(earliest)) from = earliest;
        if (from.isAfter(now)) from = earliest;

        int accepted = 0, duplicated = 0, rejected = 0, skipped = 0, requests = 0;
        Instant cursor = from;
        while (cursor.isBefore(now)) {
            Instant chunkEnd = cursor.plus(Duration.ofDays(CHUNK_DAYS));
            if (chunkEnd.isAfter(now)) chunkEnd = now;
            String payload = HealthConnectReader.buildPayloadBlocking(context, groups, cursor, chunkEnd);
            // 空窗不请求:没戴表/没授权那几天不该在服务端留下一次「0 条」的审计噪音
            if (!isEmpty(payload)) {
                String body = SimpleHttp.postJson(ApiBase.URL + "/health/ingest", token, payload);
                JSONObject res = new JSONObject(body);
                if (res.optInt("code", -1) != 0) {
                    throw new IllegalStateException(res.optString("message", "上报失败"));
                }
                JSONObject data = res.optJSONObject("data");
                if (data != null) {
                    accepted += data.optInt("accepted", 0);
                    duplicated += data.optInt("duplicated", 0);
                    rejected += data.optInt("rejected", 0);
                    skipped += data.optInt("skipped", 0);
                }
                requests++;
            }
            cursor = chunkEnd;
        }

        JSONObject result = new JSONObject();
        result.put("accepted", accepted);
        result.put("duplicated", duplicated);
        result.put("rejected", rejected);
        result.put("skipped", skipped);
        result.put("requests", requests);
        result.put("from", from.toString());
        result.put("to", now.toString());
        HealthPrefs.markSync(context, true, "接收 " + accepted + " 条,拒收 " + rejected + " 条");
        return result;
    }

    /** 请求体里 samples/sleep 都空 = 这段窗口没有数据 */
    private static boolean isEmpty(String payload) {
        try {
            JSONObject json = new JSONObject(payload);
            return json.optJSONArray("samples") == null
                    || (json.optJSONArray("samples").length() == 0
                    && (json.optJSONArray("sleep") == null || json.optJSONArray("sleep").length() == 0));
        } catch (Exception e) {
            return false;
        }
    }
}
