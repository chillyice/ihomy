package top.ihomy.app.health

import android.content.Context
import android.content.Intent
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BasalBodyTemperatureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.reflect.KClass

/**
 * Health Connect 读取与聚合(Android 侧唯一实现)。
 *
 * 口径与后端 `common/HealthMetric` / `HealthSleepAggregator` 对齐:
 * - 日聚合指标(步数/距离/能量/楼层/锻炼分钟)按设备当地日汇总,一个来源 App 一行;
 * - 心率只在本机按「小时桶」聚合(avg/min/max/count),绝不把逐点样本上报(服务端 2GB 内存);
 * - 睡眠传「分期区间」,由服务端合并会话(与 iOS 快捷指令同一张表、同一套规则);
 * - 同一指标不同来源(每个 dataOrigin.packageName)分别上报,不跨来源混算。
 */
object HealthConnectReader {

    const val PROVIDER = "HEALTH_CONNECT"

    /** 权限分组:与前端设置页、后端 `HealthMetric.group` 同名的四组 */
    @JvmField
    val GROUPS: List<String> = listOf("SLEEP", "ACTIVITY", "VITALS", "BODY")

    /** 每组覆盖的记录类型(权限字符串由库按版本生成,不手写死) */
    private val GROUP_TYPES: Map<String, List<KClass<out Record>>> = linkedMapOf(
        "SLEEP" to listOf(SleepSessionRecord::class),
        "ACTIVITY" to listOf(
            StepsRecord::class,
            DistanceRecord::class,
            ActiveCaloriesBurnedRecord::class,
            TotalCaloriesBurnedRecord::class,
            FloorsClimbedRecord::class,
            ExerciseSessionRecord::class
        ),
        "VITALS" to listOf(
            HeartRateRecord::class,
            RestingHeartRateRecord::class,
            HeartRateVariabilityRmssdRecord::class,
            OxygenSaturationRecord::class,
            RespiratoryRateRecord::class,
            BodyTemperatureRecord::class,
            BasalBodyTemperatureRecord::class
        ),
        "BODY" to listOf(WeightRecord::class, BodyFatRecord::class, HeightRecord::class)
    )

    /** 查询可用性;0 = 可用(见 HealthConnectClient.SDK_*) */
    @JvmStatic
    fun sdkStatus(context: Context): Int = try {
        HealthConnectClient.getSdkStatus(context)
    } catch (e: Throwable) {
        -1
    }

    @JvmStatic
    fun isAvailable(context: Context): Boolean =
        sdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    /** 一组里全部记录类型的读权限 + 后台读取(Android 15 起才生效,旧系统忽略) */
    private fun permissionsFor(groups: List<String>): Set<String> {
        val out = mutableSetOf<String>()
        for (g in groups) {
            GROUP_TYPES[g.uppercase()]?.forEach { out.add(HealthPermission.getReadPermission(it)) }
        }
        if (out.isNotEmpty()) out.add(HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND)
        return out
    }

    /** 权限申请用的系统弹窗 Intent(由插件用 startActivityForResult 拉起) */
    @JvmStatic
    fun requestPermissionsIntent(context: Context, groups: List<String>): Intent {
        val contract = PermissionController.createRequestPermissionResultContract()
        return contract.createIntent(context, permissionsFor(groups))
    }

    /**
     * 已授权分组:一组内所有记录类型的权限都在才算该组已连接(部分授权按未授权处理,下次申请会补齐)。
     */
    @JvmStatic
    fun grantedGroupsBlocking(context: Context, groups: List<String>): List<String> = runBlocking {
        if (!isAvailable(context)) return@runBlocking emptyList()
        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        groups.map { it.uppercase() }.filter { g ->
            val types = GROUP_TYPES[g].orEmpty()
            types.isNotEmpty() && types.all { HealthPermission.getReadPermission(it) in granted }
        }
    }

    /** 每组授权状态,给设置页显示「睡眠已连接 / 体征未授权」 */
    @JvmStatic
    fun groupStatusBlocking(context: Context, groups: List<String>): Map<String, Boolean> = runBlocking {
        if (!isAvailable(context)) return@runBlocking groups.associate { it.uppercase() to false }
        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        groups.map { it.uppercase() }.associateWith { g ->
            val types = GROUP_TYPES[g].orEmpty()
            types.isNotEmpty() && types.all { HealthPermission.getReadPermission(it) in granted }
        }
    }

    /**
     * 读一段时间窗内已授权组的数据,产出 `/health/ingest` 请求体(JSON 字符串)。
     * 调用方按 ≤7 天切窗,避免单请求超过后端 2000 条上限。
     */
    @JvmStatic
    fun buildPayloadBlocking(context: Context, groups: List<String>, start: Instant, end: Instant): String {
        val samples = JSONArray()
        val sleep = JSONArray()
        if (isAvailable(context)) {
            val client = HealthConnectClient.getOrCreate(context)
            val wanted = groups.map { it.uppercase() }.toSet()
            runBlocking {
                if ("SLEEP" in wanted) collectSleep(client, start, end, sleep)
                if ("ACTIVITY" in wanted) collectActivity(client, start, end, samples)
                if ("VITALS" in wanted) collectVitals(client, start, end, samples)
                if ("BODY" in wanted) collectBody(client, start, end, samples)
            }
        }
        return JSONObject()
            .put("provider", PROVIDER)
            .put("timezone", OffsetDateTime.now().offset.id)
            .put("samples", samples)
            .put("sleep", sleep)
            .toString()
    }

    // ==================== 读取 ====================

    /** 分页读完一个类型(服务端按页给 pageToken,单页 1000 条) */
    private suspend fun <T : Record> forEachRecord(
        client: HealthConnectClient,
        type: KClass<T>,
        start: Instant,
        end: Instant,
        block: (T) -> Unit
    ) {
        var token: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = type,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    pageSize = 1000,
                    pageToken = token
                )
            )
            response.records.forEach(block)
            token = response.pageToken
        } while (token != null)
    }

    private fun origin(record: Record): String =
        record.metadata.dataOrigin.packageName.ifBlank { "OTHER" }

    private fun dateOf(instant: Instant): String =
        instant.atZone(ZoneId.systemDefault()).toLocalDate().toString()

    private fun addDaySample(
        out: JSONArray, metric: String, date: String,
        value: Double, pkg: String
    ) {
        // 0 值不占行:步数 0 / 能量 0 这类「那天没数据」的行没意义
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

    /** 日累计值:key = (来源包名, 当地日) */
    private fun addTo(map: MutableMap<Pair<String, String>, Double>, pkg: String, date: String, v: Double) {
        val key = pkg to date
        map[key] = (map[key] ?: 0.0) + v
    }

    private suspend fun collectActivity(client: HealthConnectClient, start: Instant, end: Instant, out: JSONArray) {
        val steps = HashMap<Pair<String, String>, Double>()
        forEachRecord(client, StepsRecord::class, start, end) { r ->
            addTo(steps, origin(r), dateOf(r.startTime), r.count.toDouble())
        }
        steps.forEach { (k, v) -> addDaySample(out, "STEPS", k.second, v, k.first) }

        val distance = HashMap<Pair<String, String>, Double>()
        forEachRecord(client, DistanceRecord::class, start, end) { r ->
            addTo(distance, origin(r), dateOf(r.startTime), r.distance.inKilometers)
        }
        distance.forEach { (k, v) -> addDaySample(out, "DISTANCE", k.second, v, k.first) }

        val active = HashMap<Pair<String, String>, Double>()
        forEachRecord(client, ActiveCaloriesBurnedRecord::class, start, end) { r ->
            addTo(active, origin(r), dateOf(r.startTime), r.energy.inKilocalories)
        }
        active.forEach { (k, v) -> addDaySample(out, "ACTIVE_ENERGY", k.second, v, k.first) }

        val total = HashMap<Pair<String, String>, Double>()
        forEachRecord(client, TotalCaloriesBurnedRecord::class, start, end) { r ->
            addTo(total, origin(r), dateOf(r.startTime), r.energy.inKilocalories)
        }
        total.forEach { (k, v) -> addDaySample(out, "TOTAL_ENERGY", k.second, v, k.first) }

        val floors = HashMap<Pair<String, String>, Double>()
        forEachRecord(client, FloorsClimbedRecord::class, start, end) { r ->
            addTo(floors, origin(r), dateOf(r.startTime), r.floors)
        }
        floors.forEach { (k, v) -> addDaySample(out, "FLIGHTS", k.second, v, k.first) }

        val exercise = HashMap<Pair<String, String>, Double>()
        forEachRecord(client, ExerciseSessionRecord::class, start, end) { r ->
            val minutes = Duration.between(r.startTime, r.endTime).toMinutes().toDouble()
            if (minutes > 0) addTo(exercise, origin(r), dateOf(r.startTime), minutes)
        }
        exercise.forEach { (k, v) -> addDaySample(out, "EXERCISE_MIN", k.second, v, k.first) }
    }

    /** 小时桶(心率):按来源 + 当地小时累计,最后发 avg/min/max/count */
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

    /** 日平均累计(静息心率/HRV/呼吸率/基础体温) */
    private class DayAvg {
        var sum = 0.0
        var count = 0

        fun add(v: Double) {
            sum += v
            count++
        }

        fun avg(): Double = if (count == 0) 0.0 else sum / count
    }

    private suspend fun collectVitals(client: HealthConnectClient, start: Instant, end: Instant, out: JSONArray) {
        val zone = ZoneId.systemDefault()
        val buckets = HashMap<Pair<String, Instant>, HourBucket>()
        forEachRecord(client, HeartRateRecord::class, start, end) { r ->
            val pkg = origin(r)
            for (s in r.samples) {
                val hour = s.time.atZone(zone).truncatedTo(ChronoUnit.HOURS).toInstant()
                buckets.getOrPut(pkg to hour) { HourBucket() }.add(s.beatsPerMinute.toDouble())
            }
        }
        buckets.forEach { (key, b) ->
            if (b.count == 0) return@forEach
            val hourStart = key.second
            out.put(
                JSONObject()
                    .put("metric", "HEART_RATE")
                    .put("period", "POINT")
                    .put("start", hourStart.toString())
                    .put("end", hourStart.plus(Duration.ofHours(1)).toString())
                    .put("avg", b.sum / b.count)
                    .put("min", b.min)
                    .put("max", b.max)
                    .put("count", b.count)
                    .put("source", key.first)
                    .put("sourceApp", key.first)
            )
        }

        val resting = HashMap<Pair<String, String>, DayAvg>()
        forEachRecord(client, RestingHeartRateRecord::class, start, end) { r ->
            resting.getOrPut(origin(r) to dateOf(r.time)) { DayAvg() }.add(r.beatsPerMinute.toDouble())
        }
        resting.forEach { (k, a) -> addDaySample(out, "RESTING_HR", k.second, a.avg(), k.first) }

        val hrv = HashMap<Pair<String, String>, DayAvg>()
        forEachRecord(client, HeartRateVariabilityRmssdRecord::class, start, end) { r ->
            hrv.getOrPut(origin(r) to dateOf(r.time)) { DayAvg() }
                .add(r.heartRateVariabilityMillis)
        }
        hrv.forEach { (k, a) -> addDaySample(out, "HRV", k.second, a.avg(), k.first) }

        val resp = HashMap<Pair<String, String>, DayAvg>()
        forEachRecord(client, RespiratoryRateRecord::class, start, end) { r ->
            resp.getOrPut(origin(r) to dateOf(r.time)) { DayAvg() }.add(r.rate)
        }
        resp.forEach { (k, a) -> addDaySample(out, "RESP_RATE", k.second, a.avg(), k.first) }

        forEachRecord(client, OxygenSaturationRecord::class, start, end) { r ->
            addPointSample(out, "SPO2", r.time, r.percentage.value, origin(r))
        }

        forEachRecord(client, BodyTemperatureRecord::class, start, end) { r ->
            addPointSample(out, "BODY_TEMP", r.time, r.temperature.inCelsius, origin(r))
        }

        val basal = HashMap<Pair<String, String>, DayAvg>()
        forEachRecord(client, BasalBodyTemperatureRecord::class, start, end) { r ->
            basal.getOrPut(origin(r) to dateOf(r.time)) { DayAvg() }.add(r.temperature.inCelsius)
        }
        basal.forEach { (k, a) -> addDaySample(out, "BASAL_TEMP", k.second, a.avg(), k.first) }
    }

    private suspend fun collectBody(client: HealthConnectClient, start: Instant, end: Instant, out: JSONArray) {
        forEachRecord(client, WeightRecord::class, start, end) { r ->
            addPointSample(out, "WEIGHT", r.time, r.weight.inKilograms, origin(r))
        }
        forEachRecord(client, BodyFatRecord::class, start, end) { r ->
            addPointSample(out, "BODY_FAT", r.time, r.percentage.value, origin(r))
        }
        forEachRecord(client, HeightRecord::class, start, end) { r ->
            addPointSample(out, "HEIGHT", r.time, r.height.inMeters * 100.0, origin(r))
        }
    }

    /**
     * 睡眠:逐段分期区间上报(不聚合)。
     * 没有分期明细的会话按「整段在床」上报,服务端据此得到 stage_level=NONE 的会话;
     * 离开床铺的段不参与(不在床上 ≠ 在床时长)。
     */
    private suspend fun collectSleep(client: HealthConnectClient, start: Instant, end: Instant, out: JSONArray) {
        forEachRecord(client, SleepSessionRecord::class, start, end) { r ->
            val pkg = origin(r)
            if (r.stages.isEmpty()) {
                putSleep(out, r.startTime, r.endTime, "INBED", pkg)
            } else {
                for (s in r.stages) {
                    val stage = when (s.stage) {
                        SleepSessionRecord.STAGE_TYPE_AWAKE -> "AWAKE"
                        SleepSessionRecord.STAGE_TYPE_SLEEPING -> "LIGHT"
                        SleepSessionRecord.STAGE_TYPE_LIGHT -> "LIGHT"
                        SleepSessionRecord.STAGE_TYPE_DEEP -> "DEEP"
                        SleepSessionRecord.STAGE_TYPE_REM -> "REM"
                        SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> null
                        else -> "UNKNOWN"
                    } ?: continue
                    putSleep(out, s.startTime, s.endTime, stage, pkg)
                }
            }
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
