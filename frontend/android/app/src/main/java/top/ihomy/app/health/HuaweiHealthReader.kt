package top.ihomy.app.health

import android.content.Context
import android.content.pm.PackageManager
import com.huawei.hmf.tasks.Tasks
import com.huawei.hms.api.HuaweiApiAvailability
import com.huawei.hms.hihealth.HiHealthOptions
import com.huawei.hms.hihealth.HuaweiHiHealth
import com.huawei.hms.hihealth.data.DataType
import com.huawei.hms.hihealth.options.ReadOptions
import com.huawei.hms.support.hwid.HuaweiIdAuthManager
import com.huawei.hms.support.hwid.result.AuthHuaweiId
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * 华为运动健康(HMS Health Kit)读取与聚合——无 Health Connect 的华为/鸿蒙机的第二实现。
 *
 * 产出契约与 [HealthConnectReader] 完全一致(同样的指标代码/日聚合/心率小时桶/睡眠分期),
 * 只是 provider=HUAWEI_HEALTH、数据来自华为运动健康。前端/服务端不需要为华为改口径。
 *
 * 前置条件(见 docs/daily 的 AGC 清单):
 * - manifest 里 `com.huawei.hms.client.appid`(AGC 应用 id,`appid=<数字>`);
 * - AGC 开通 Health Kit 并批准数据类型 scope;
 * - APK 用 AGC 里登记的签名证书指纹签署(release 用 ihomy-release.jks);
 * - 手机装有 HMS Core 与华为运动健康(华为机默认都有)。
 * 未配置时 isAvailable=false,插件自动回落 Health Connect/不可用,不影响其他机型。
 */
object HuaweiHealthReader {

    const val PROVIDER = "HUAWEI_HEALTH"

    /** 权限分组:与 HealthConnectReader/前端/后端同名的四组 */
    @JvmField
    val GROUPS: List<String> = HealthConnectReader.GROUPS

    /** 每组覆盖的数据类型(华为子集:血氧/HRV/呼吸率/体温/楼层 Health Kit 无对应类型,不采) */
    private val GROUP_TYPES: Map<String, List<DataType>> = linkedMapOf(
        "SLEEP" to listOf(DataType.DT_CONTINUOUS_SLEEP),
        "ACTIVITY" to listOf(
            DataType.DT_CONTINUOUS_STEPS_DELTA,
            DataType.DT_CONTINUOUS_DISTANCE_DELTA,
            DataType.DT_CONTINUOUS_CALORIES_BURNT,
            DataType.DT_CONTINUOUS_ACTIVITY_SEGMENT
        ),
        "VITALS" to listOf(
            DataType.DT_INSTANTANEOUS_HEART_RATE,
            DataType.DT_INSTANTANEOUS_RESTING_HEART_RATE
        ),
        "BODY" to listOf(
            DataType.DT_INSTANTANEOUS_BODY_WEIGHT,
            DataType.DT_INSTANTANEOUS_BODY_FAT_RATE,
            DataType.DT_INSTANTANEOUS_HEIGHT
        )
    )

    /** HMS SDK 从 manifest meta-data 读 AGC appid;没配就当不可用 */
    private const val HMS_APPID_META = "com.huawei.hms.client.appid"
    private const val HUAWEI_HEALTH_PKG = "com.huawei.health"

    @JvmStatic
    fun sdkStatus(context: Context): String {
        return try {
            if (HuaweiApiAvailability.getInstance().isHuaweiMobileServicesAvailable(context) != 0) {
                "NO_HMS"
            } else if (!hasHuaweiHealth(context)) {
                "NO_HEALTH_APP"
            } else if (appidOf(context).isBlank()) {
                "NO_APPID"
            } else {
                "OK"
            }
        } catch (e: Throwable) {
            "NO_HMS"
        }
    }

    @JvmStatic
    fun isAvailable(context: Context): Boolean = sdkStatus(context) == "OK"

    private fun appidOf(context: Context): String {
        return try {
            val ai = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
            ai.metaData?.getString(HMS_APPID_META)?.trim()?.removePrefix("appid=") ?: ""
        } catch (e: Throwable) {
            ""
        }
    }

    private fun hasHuaweiHealth(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(HUAWEI_HEALTH_PKG, 0) != null
    } catch (e: Throwable) {
        false
    }

    // ==================== 授权 ====================

    /** 每组需要的 scope-uri 集(由 DataType 自带的 scope 名推导,不手写死) */
    private fun groupScopeUris(group: String): Set<String> =
        GROUP_TYPES[group.uppercase()]?.mapNotNull { it.scopeNameRead }?.toSet() ?: emptySet()

    private fun optionsFor(groups: List<String>): HiHealthOptions {
        val builder = HiHealthOptions.builder()
        for (g in groups) {
            GROUP_TYPES[g.uppercase()]?.forEach { builder.addDataType(it, HiHealthOptions.ACCESS_READ) }
        }
        return builder.build()
    }

    /**
     * 静默取授权身份:已授权直接返回,未授权时该调用会拉起 HMS 授权页(只在设置页前台走)。
     * 失败抛异常,调用方按未授权/不可用处理。HMS 的扩展授权是同步调用。
     */
    private fun authBlocking(context: Context): AuthHuaweiId =
        HuaweiIdAuthManager.getExtendedAuthResult(optionsFor(GROUPS))

    /** 一组内全部 scope-uri 都在授权列表里才算该组已连接(与 HealthConnectReader 同口径) */
    @JvmStatic
    fun grantedGroupsBlocking(context: Context, groups: List<String>): List<String> {
        val id = authBlocking(context)
        val scopes = id.authorizedScopes.map { it.scopeUri }.toSet()
        return groups.map { it.uppercase() }.filter { g ->
            val want = groupScopeUris(g)
            want.isNotEmpty() && scopes.containsAll(want)
        }
    }

    @JvmStatic
    fun groupStatusBlocking(context: Context, groups: List<String>): Map<String, Boolean> {
        val id = authBlocking(context)
        val scopes = id.authorizedScopes.map { it.scopeUri }.toSet()
        return groups.map { it.uppercase() }.associateWith { g ->
            val want = groupScopeUris(g)
            want.isNotEmpty() && scopes.containsAll(want)
        }
    }

    /**
     * 拉起 HMS 扩展授权(未授权会弹窗,由 HMS 自己管理界面),结束后回调已授权分组。
     * 与 Health Connect 的 startActivityForResult 流不同,HMS 扩展授权是同步调用,
     * 授权页由 HMS 内部管理——必须在主线程调,结果切回调用方给的回调。
     */
    @JvmStatic
    fun requestAuthorization(
        activity: android.app.Activity,
        groups: List<String>,
        onDone: (List<String>) -> Unit,
        onError: (String) -> Unit
    ) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                val id = HuaweiIdAuthManager.getExtendedAuthResult(optionsFor(groups))
                val scopes = id.authorizedScopes.map { it.scopeUri }.toSet()
                val granted = groups.map { it.uppercase() }.filter { g ->
                    val want = groupScopeUris(g)
                    want.isNotEmpty() && scopes.containsAll(want)
                }
                onDone(granted)
            } catch (e: Throwable) {
                onError(e.message ?: "auth_failed")
            }
        }
    }

    // ==================== 读取 ====================

    /** 读一段时间窗内已授权组的数据,产出 `/health/ingest` 请求体(与 HealthConnectReader 同形) */
    @JvmStatic
    fun buildPayloadBlocking(context: Context, groups: List<String>, start: Instant, end: Instant): String {
        val samples = JSONArray()
        val sleep = JSONArray()
        val id = authBlocking(context)
        val client = HuaweiHiHealth.getDataController(context, id)
        val wanted = groups.map { it.uppercase() }.toSet()
        if ("SLEEP" in wanted) collectSleep(client, start, end, sleep)
        if ("ACTIVITY" in wanted) collectActivity(client, start, end, samples)
        if ("VITALS" in wanted) collectVitals(client, start, end, samples)
        if ("BODY" in wanted) collectBody(client, start, end, samples)
        return JSONObject()
            .put("provider", PROVIDER)
            .put("timezone", OffsetDateTime.now().offset.id)
            .put("samples", samples)
            .put("sleep", sleep)
            .toString()
    }

    private fun readPoints(client: com.huawei.hms.hihealth.DataController, type: DataType, start: Instant, end: Instant): List<com.huawei.hms.hihealth.data.SamplePoint> {
        val options = ReadOptions.Builder()
            .read(type)
            .setTimeRange(start.toEpochMilli(), end.toEpochMilli(), TimeUnit.MILLISECONDS)
            .setPageSize(5000)
            .build()
        val reply = Tasks.await(client.read(options))
        val set = reply.getSampleSet(type) ?: return emptyList()
        return set.samplePoints ?: emptyList()
    }

    /** 类型只有一个主数值字段,字段名按华为文档各版本有差异,直接取第一个数值 */
    private fun firstNumeric(point: com.huawei.hms.hihealth.data.SamplePoint): Double? {
        val values = point.fieldValues ?: return null
        for ((_, v) in values) {
            try {
                if (v != null && v.isSet) return v.asDoubleValue()
            } catch (e: Throwable) {
                continue
            }
        }
        return null
    }

    private fun origin(point: com.huawei.hms.hihealth.data.SamplePoint): String =
        point.dataCollector?.packageName?.ifBlank { "HUAWEI_HEALTH" } ?: "HUAWEI_HEALTH"

    private fun dateOf(instant: Instant): String =
        instant.atZone(ZoneId.systemDefault()).toLocalDate().toString()

    private fun addDaySample(out: JSONArray, metric: String, date: String, value: Double, pkg: String) {
        if (value <= 0.0) return
        out.put(
            JSONObject()
                .put("metric", metric)
                .put("period", "DAY")
                .put("date", date)
                .put("value", value)
                .put("source", pkg)
                .put("sourceApp", pkg)
        )
    }

    private fun addTo(map: MutableMap<Pair<String, String>, Double>, pkg: String, date: String, v: Double) {
        val key = pkg to date
        map[key] = (map[key] ?: 0.0) + v
    }

    private fun collectActivity(
        client: com.huawei.hms.hihealth.DataController,
        start: Instant,
        end: Instant,
        out: JSONArray
    ) {
        val steps = HashMap<Pair<String, String>, Double>()
        readPoints(client, DataType.DT_CONTINUOUS_STEPS_DELTA, start, end).forEach { p ->
            val v = firstNumeric(p) ?: return@forEach
            if (v > 0) addTo(steps, origin(p), dateOf(Instant.ofEpochMilli(p.getStartTime(TimeUnit.MILLISECONDS))), v)
        }
        steps.forEach { (k, v) -> addDaySample(out, "STEPS", k.second, v, k.first) }

        val distance = HashMap<Pair<String, String>, Double>()
        readPoints(client, DataType.DT_CONTINUOUS_DISTANCE_DELTA, start, end).forEach { p ->
            val v = firstNumeric(p) ?: return@forEach
            if (v > 0) addTo(distance, origin(p), dateOf(Instant.ofEpochMilli(p.getStartTime(TimeUnit.MILLISECONDS))), v / 1000.0)
        }
        distance.forEach { (k, v) -> addDaySample(out, "DISTANCE", k.second, v, k.first) }

        val active = HashMap<Pair<String, String>, Double>()
        readPoints(client, DataType.DT_CONTINUOUS_CALORIES_BURNT, start, end).forEach { p ->
            val v = firstNumeric(p) ?: return@forEach
            if (v > 0) addTo(active, origin(p), dateOf(Instant.ofEpochMilli(p.getStartTime(TimeUnit.MILLISECONDS))), v)
        }
        active.forEach { (k, v) -> addDaySample(out, "ACTIVE_ENERGY", k.second, v, k.first) }

        // 华为没有「总消耗」直接类型,用 BMR 近似补 TOTAL_ENERGY(服务端有基线校验,不合适会被拒收)
        val bmr = HashMap<Pair<String, String>, Double>()
        readPoints(client, DataType.DT_INSTANTANEOUS_CALORIES_BMR, start, end).forEach { p ->
            val v = firstNumeric(p) ?: return@forEach
            if (v > 0) addTo(bmr, origin(p), dateOf(Instant.ofEpochMilli(p.getSamplingTime(TimeUnit.MILLISECONDS))), v)
        }
        bmr.forEach { (k, v) -> addDaySample(out, "TOTAL_ENERGY", k.second, v, k.first) }

        // 锻炼分钟:活动段(锻炼记录)时长
        val exercise = HashMap<Pair<String, String>, Double>()
        readPoints(client, DataType.DT_CONTINUOUS_ACTIVITY_SEGMENT, start, end).forEach { p ->
            val st = Instant.ofEpochMilli(p.getStartTime(TimeUnit.MILLISECONDS))
            val en = Instant.ofEpochMilli(p.getEndTime(TimeUnit.MILLISECONDS))
            val minutes = java.time.Duration.between(st, en).toMinutes().toDouble()
            if (minutes > 0) addTo(exercise, origin(p), dateOf(st), minutes)
        }
        exercise.forEach { (k, v) -> addDaySample(out, "EXERCISE_MIN", k.second, v, k.first) }
    }

    private class HourBucket {
        var sum = 0.0
        var min = Double.MAX_VALUE
        var max = -Double.MAX_VALUE
        var count = 0

        fun add(v: Double) {
            sum += v
            if (v < min) min = v
            if (v > max) max = v
            count++
        }
    }

    private class DayAvg {
        var sum = 0.0
        var count = 0

        fun add(v: Double) {
            sum += v
            count++
        }

        fun avg(): Double = if (count == 0) 0.0 else sum / count
    }

    private fun collectVitals(
        client: com.huawei.hms.hihealth.DataController,
        start: Instant,
        end: Instant,
        out: JSONArray
    ) {
        val zone = ZoneId.systemDefault()
        val buckets = HashMap<Pair<String, Instant>, HourBucket>()
        readPoints(client, DataType.DT_INSTANTANEOUS_HEART_RATE, start, end).forEach { p ->
            val v = firstNumeric(p) ?: return@forEach
            if (v <= 0) return@forEach
            val pkg = origin(p)
            val at = Instant.ofEpochMilli(p.getSamplingTime(TimeUnit.MILLISECONDS))
            val hour = at.atZone(zone).truncatedTo(ChronoUnit.HOURS).toInstant()
            buckets.getOrPut(pkg to hour) { HourBucket() }.add(v)
        }
        buckets.forEach { (key, b) ->
            if (b.count == 0) return@forEach
            val hourStart = key.second
            out.put(
                JSONObject()
                    .put("metric", "HEART_RATE")
                    .put("period", "POINT")
                    .put("start", hourStart.toString())
                    .put("end", hourStart.plus(java.time.Duration.ofHours(1)).toString())
                    .put("avg", b.sum / b.count)
                    .put("min", b.min)
                    .put("max", b.max)
                    .put("count", b.count)
                    .put("source", key.first)
                    .put("sourceApp", key.first)
            )
        }

        val resting = HashMap<Pair<String, String>, DayAvg>()
        readPoints(client, DataType.DT_INSTANTANEOUS_RESTING_HEART_RATE, start, end).forEach { p ->
            val v = firstNumeric(p) ?: return@forEach
            if (v > 0) resting.getOrPut(origin(p) to dateOf(Instant.ofEpochMilli(p.getSamplingTime(TimeUnit.MILLISECONDS)))) { DayAvg() }.add(v)
        }
        resting.forEach { (k, a) -> addDaySample(out, "RESTING_HR", k.second, a.avg(), k.first) }
    }

    private fun collectBody(
        client: com.huawei.hms.hihealth.DataController,
        start: Instant,
        end: Instant,
        out: JSONArray
    ) {
        // 体重:华为按千克;离谱值(>400)直接丢弃不报
        readPoints(client, DataType.DT_INSTANTANEOUS_BODY_WEIGHT, start, end).forEach { p ->
            val v = firstNumeric(p) ?: return@forEach
            if (v < 25.0 || v > 400.0) return@forEach
            addPointSample(out, "WEIGHT", Instant.ofEpochMilli(p.getSamplingTime(TimeUnit.MILLISECONDS)), v, origin(p))
        }
        // 体脂率:华为按百分数(0~100),归一到 Health Connect 同款小数(0~1)
        readPoints(client, DataType.DT_INSTANTANEOUS_BODY_FAT_RATE, start, end).forEach { p ->
            var v = firstNumeric(p) ?: return@forEach
            if (v > 1.5) v /= 100.0
            if (v < 0.01 || v > 0.7) return@forEach
            addPointSample(out, "BODY_FAT", Instant.ofEpochMilli(p.getSamplingTime(TimeUnit.MILLISECONDS)), v, origin(p))
        }
        // 身高:华为按微米,归一到厘米(与 HealthConnectReader 的输出同单位)
        readPoints(client, DataType.DT_INSTANTANEOUS_HEIGHT, start, end).forEach { p ->
            var v = firstNumeric(p) ?: return@forEach
            v = when {
                v > 10000.0 -> v / 1_000_000.0
                v > 3.0 -> v / 100.0
                else -> v
            }
            if (v < 30.0 || v > 260.0) return@forEach
            addPointSample(out, "HEIGHT", Instant.ofEpochMilli(p.getSamplingTime(TimeUnit.MILLISECONDS)), v, origin(p))
        }
    }

    private fun addPointSample(out: JSONArray, metric: String, at: Instant, value: Double, pkg: String) {
        if (value <= 0.0) return
        out.put(
            JSONObject()
                .put("metric", metric)
                .put("period", "POINT")
                .put("at", at.toString())
                .put("value", value)
                .put("source", pkg)
                .put("sourceApp", pkg)
        )
    }

    /**
     * 睡眠:逐段分期上报。华为的分期落在子类型名上(DataType.name 带 DEEP/LIGHT/REM/AWAKE 后缀),
     * 整段无分期的按 INBED 上报;离床段与识别不了的段不参与。
     */
    private fun collectSleep(
        client: com.huawei.hms.hihealth.DataController,
        start: Instant,
        end: Instant,
        out: JSONArray
    ) {
        readPoints(client, DataType.DT_CONTINUOUS_SLEEP, start, end).forEach { p ->
            val pkg = origin(p)
            val st = Instant.ofEpochMilli(p.getStartTime(TimeUnit.MILLISECONDS))
            val en = Instant.ofEpochMilli(p.getEndTime(TimeUnit.MILLISECONDS))
            val name = (p.dataType?.name ?: "").uppercase()
            val stage = when {
                name.contains("DEEP") -> "DEEP"
                name.contains("REM") -> "REM"
                name.contains("AWAKE") -> "AWAKE"
                name.contains("ON_OFF") -> null
                name.contains("LIGHT") || name.contains("SLEEP") -> "LIGHT"
                else -> "INBED"
            } ?: return@forEach
            putSleep(out, st, en, stage, pkg)
        }
    }

    private fun putSleep(out: JSONArray, start: Instant, end: Instant, stage: String, pkg: String) {
        if (!end.isAfter(start)) return
        out.put(
            JSONObject()
                .put("start", start.toString())
                .put("end", end.toString())
                .put("stage", stage)
                .put("source", pkg)
                .put("sourceApp", pkg)
        )
    }
}
