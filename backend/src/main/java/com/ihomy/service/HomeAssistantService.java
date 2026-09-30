package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ihomy.common.BizException;
import com.ihomy.common.HttpUrlUtil;
import com.ihomy.common.ResultCode;
import com.ihomy.common.ThirdPartyHttp;
import com.ihomy.dto.IotConfigDTO;
import com.ihomy.dto.IotControlDTO;
import com.ihomy.dto.IotDeviceDTO;
import com.ihomy.entity.IotConfig;
import com.ihomy.entity.IotData;
import com.ihomy.entity.IotDevice;
import com.ihomy.mapper.IotConfigMapper;
import com.ihomy.mapper.IotDataMapper;
import com.ihomy.mapper.IotDeviceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 智能家居中控(Home Assistant 接入):
 * ihomy 只做「数据沉淀 + 家人控制入口」,协议与设备接入全归 HA——
 * 读实体状态走 GET /api/states(后端每分钟轮询),控制走 POST /api/services/{domain}/{service}。
 *
 * 每家庭一条接入配置({@link IotConfig},站点地址 + 长期访问令牌 ENC 加密);
 * 设备与历史是从 HA 同步来的派生数据,按 family_id 隔离,移除接入时一并清掉。
 * 采样节流:状态一变就记一条,不变时读数类设备最短 10 分钟记一条心跳,执行类设备只记变更。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HomeAssistantService {

    /** 关心的实体域(其余如 automation/script/person 一概不入库) */
    private static final Set<String> TRACKED = Set.of(
            "sensor", "binary_sensor", "switch", "light", "climate", "cover", "fan",
            "lock", "humidifier", "number", "input_boolean", "input_number", "media_player");

    /** 可在中控页直接控制的域 */
    private static final Set<String> CONTROLLABLE = Set.of(
            "switch", "light", "fan", "cover", "lock", "humidifier", "climate",
            "number", "input_boolean", "input_number", "media_player");

    /** 允许调用的 HA 服务名(域+服务是否匹配由 HA 校验,这里只挡掉任意 URL 拼接) */
    private static final Set<String> SERVICES = Set.of(
            "turn_on", "turn_off", "toggle",
            "open_cover", "close_cover", "stop_cover",
            "lock", "unlock",
            "set_hvac_mode", "set_temperature", "set_humidity",
            "set_value",
            "play", "pause", "media_play", "media_pause",
            "volume_set", "volume_up", "volume_down");

    /** 读数类设备:状态没变也按心跳补一条历史曲线;执行类(开关灯)只记真实变更 */
    private static final Set<String> HEARTBEAT = Set.of("sensor", "binary_sensor", "climate", "number", "input_number");

    private static final Duration SAMPLE_INTERVAL = Duration.ofMinutes(10);
    private static final int TIMEOUT_MS = 8000;
    private static final int TEST_TIMEOUT_MS = 6000;
    private static final int HISTORY_HOURS_MAX = 24 * 30;
    private static final int HISTORY_LIMIT = 2000;
    private static final int MAX_NAME = 100;
    private static final int MAX_STATE = 255;

    private static final Pattern ENTITY_ID = Pattern.compile("^[a-z0-9_]+\\.[A-Za-z0-9_]+$");

    private final IotConfigMapper configMapper;
    private final IotDeviceMapper deviceMapper;
    private final IotDataMapper dataMapper;
    private final ParameterService parameterService;
    private final ObjectMapper json;

    // ==================== 配置 ====================

    /** 配置视图:令牌只回是否已设置,不回明文/密文 */
    public Map<String, Object> configView(Long familyId) {
        IotConfig row = find(familyId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("configured", row != null);
        m.put("baseUrl", row == null ? null : row.getBaseUrl());
        m.put("hasToken", row != null && notBlank(row.getToken()));
        m.put("enabled", row == null || row.getEnabled() == null || row.getEnabled() == 1);
        m.put("lastSyncAt", row == null ? null : row.getLastSyncAt());
        m.put("lastError", row == null ? null : row.getLastError());
        m.put("deviceCount", familyId == null ? 0
                : deviceMapper.selectCount(new LambdaQueryWrapper<IotDevice>().eq(IotDevice::getFamilyId, familyId)));
        return m;
    }

    public Map<String, Object> saveConfig(Long familyId, Long userId, IotConfigDTO dto) {
        if (familyId == null) throw new BizException(ResultCode.BAD_REQUEST, "请先加入一个家庭");
        String baseUrl = HttpUrlUtil.normalize(dto.getBaseUrl(), "请填写 Home Assistant 地址");
        IotConfig row = find(familyId);
        boolean isNew = row == null;
        if (isNew) {
            row = new IotConfig();
            row.setFamilyId(familyId);
            row.setCreatedBy(userId);
        }
        String token = row.getToken();
        if (notBlank(dto.getToken())) token = parameterService.encrypt(dto.getToken().trim());
        if (!notBlank(token)) throw new BizException(ResultCode.BAD_REQUEST, "请填写长期访问令牌");
        int enabled;
        if (dto.getEnabled() != null) {
            enabled = dto.getEnabled() ? 1 : 0;
        } else if (isNew) {
            enabled = 1;
        } else {
            enabled = row.getEnabled() == null ? 1 : row.getEnabled();
        }

        if (isNew) {
            row.setBaseUrl(baseUrl);
            row.setToken(token);
            row.setEnabled(enabled);
            configMapper.insert(row);
        } else {
            configMapper.update(null, new LambdaUpdateWrapper<IotConfig>()
                    .eq(IotConfig::getId, row.getId())
                    .set(IotConfig::getBaseUrl, baseUrl)
                    .set(IotConfig::getToken, token)
                    .set(IotConfig::getEnabled, enabled));
        }
        return configView(familyId);
    }

    /** 移除接入:配置 + 同步来的设备与历史一并清掉(数据源仍是 HA,重新接入会再同步回来) */
    public void removeConfig(Long familyId) {
        if (familyId == null) return;
        IotConfig row = find(familyId);
        if (row == null) throw new BizException(ResultCode.NOT_FOUND, "尚未接入智能家居");
        List<Long> ids = deviceMapper.selectList(new LambdaQueryWrapper<IotDevice>()
                        .eq(IotDevice::getFamilyId, familyId).select(IotDevice::getId))
                .stream().map(IotDevice::getId).toList();
        if (!ids.isEmpty()) {
            dataMapper.delete(new LambdaQueryWrapper<IotData>().in(IotData::getDeviceId, ids));
        }
        deviceMapper.delete(new LambdaQueryWrapper<IotDevice>().eq(IotDevice::getFamilyId, familyId));
        configMapper.deleteById(row.getId());
    }

    /** 连通测试:未填的项沿用已保存配置(先测再存) */
    public Map<String, Object> test(Long familyId, IotConfigDTO dto) {
        IotConfig row = find(familyId);
        String url = notBlank(dto.getBaseUrl()) ? dto.getBaseUrl()
                : (row == null ? null : row.getBaseUrl());
        String token = notBlank(dto.getToken()) ? dto.getToken().trim()
                : (row == null ? null : parameterService.decrypt(row.getToken()));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", false);
        if (!notBlank(url) || !notBlank(token)) {
            m.put("message", "请填写地址与令牌");
            return m;
        }
        String base;
        try {
            base = HttpUrlUtil.normalize(url, "地址不正确");
        } catch (BizException e) {
            m.put("message", e.getMessage());
            return m;
        }
        try {
            ThirdPartyHttp.Resp r = ThirdPartyHttp.get("homeassistant", base + "/api/",
                    bearer(token), TEST_TIMEOUT_MS);
            if (!r.ok()) {
                m.put("message", httpMessage(r.status()));
                return m;
            }
            JsonNode cfg = json.readTree(r.body());
            m.put("ok", true);
            m.put("locationName", text(cfg, "location_name"));
            m.put("version", text(cfg, "version"));
        } catch (Exception e) {
            log.warn("iot test connection failed, url={}", base, e);
            m.put("message", "连不上 Home Assistant,请检查地址与网络");
        }
        return m;
    }

    // ==================== 设备 ====================

    public List<Map<String, Object>> devices(Long familyId) {
        if (familyId == null) return List.of();
        return deviceMapper.selectList(new LambdaQueryWrapper<IotDevice>()
                        .eq(IotDevice::getFamilyId, familyId)
                        .orderByAsc(IotDevice::getRoom, IotDevice::getName))
                .stream().map(this::view).toList();
    }

    public Map<String, Object> updateDevice(Long familyId, Long deviceId, IotDeviceDTO dto) {
        IotDevice d = requireDevice(familyId, deviceId, null);
        String name = notBlank(dto.getName()) ? truncate(dto.getName().trim(), MAX_NAME) : d.getName();
        String room = dto.getRoom() == null ? d.getRoom()
                : (dto.getRoom().trim().isEmpty() ? null : truncate(dto.getRoom().trim(), 50));
        int enabled = dto.getEnabled() == null ? (d.getEnabled() == null ? 1 : d.getEnabled())
                : (dto.getEnabled() ? 1 : 0);
        deviceMapper.update(null, new LambdaUpdateWrapper<IotDevice>()
                .eq(IotDevice::getId, d.getId())
                .set(IotDevice::getName, name)
                .set(IotDevice::getRoom, room)
                .set(IotDevice::getEnabled, enabled));
        d.setName(name);
        d.setRoom(room);
        d.setEnabled(enabled);
        return view(d);
    }

    public List<Map<String, Object>> history(Long familyId, Long deviceId, Integer hours) {
        requireDevice(familyId, deviceId, null);
        int h = hours == null || hours <= 0 ? 24 : Math.min(hours, HISTORY_HOURS_MAX);
        List<Map<String, Object>> out = new ArrayList<>();
        for (IotData r : dataMapper.selectList(new LambdaQueryWrapper<IotData>()
                .eq(IotData::getDeviceId, deviceId)
                .ge(IotData::getCreatedAt, LocalDateTime.now().minusHours(h))
                .orderByAsc(IotData::getCreatedAt)
                .last("limit " + HISTORY_LIMIT))) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("t", r.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            p.put("v", r.getValue());
            out.add(p);
        }
        return out;
    }

    // ==================== 控制 ====================

    public Map<String, Object> control(Long familyId, IotControlDTO dto) {
        IotConfig cfg = requireReady(familyId);
        String entityId = dto.getEntityId() == null ? "" : dto.getEntityId().trim();
        if (!ENTITY_ID.matcher(entityId).matches()) {
            throw new BizException(ResultCode.BAD_REQUEST, "设备标识不正确");
        }
        String domain = entityId.substring(0, entityId.indexOf('.'));
        if (!CONTROLLABLE.contains(domain)) {
            throw new BizException(ResultCode.BAD_REQUEST, "该设备不支持在这里控制");
        }
        String service = dto.getService() == null ? "" : dto.getService().trim();
        if (!SERVICES.contains(service)) {
            throw new BizException(ResultCode.BAD_REQUEST, "不支持这个操作");
        }
        IotDevice device = requireDevice(familyId, null, entityId);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("entity_id", entityId);
        if (dto.getData() != null) {
            dto.getData().forEach((k, v) -> {
                if (v != null && !"entity_id".equals(k)) body.put(k, v);
            });
        }
        String url = cfg.getBaseUrl() + "/api/services/" + domain + "/" + service;
        ThirdPartyHttp.Resp r;
        try {
            r = ThirdPartyHttp.request("homeassistant", "POST", url, headers(token(cfg)),
                    json.writeValueAsBytes(body), TIMEOUT_MS);
        } catch (IOException e) {
            log.error("iot control failed, entityId={}, service={}", entityId, service, e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "控制失败,连不上 Home Assistant");
        }
        if (!r.ok()) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "控制失败:" + httpMessage(r.status()));
        }
        // HA 回的是受影响实体的状态数组,取回自己那条写回库里(状态立即对得上)
        try {
            JsonNode arr = json.readTree(r.body());
            if (arr.isArray()) {
                for (JsonNode st : arr) {
                    if (entityId.equals(text(st, "entity_id"))) {
                        applyOne(device, st);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("iot control response parse skipped, entityId={}", entityId, e);
        }
        IotDevice fresh = deviceMapper.selectById(device.getId());
        return view(fresh == null ? device : fresh);
    }

    // ==================== 同步(由 IotSyncService 定时调) ====================

    /** 拉一次 HA 全量实体状态并入库;失败只写 last_error,不抛给调度器 */
    public void sync(IotConfig cfg) {
        String url = cfg.getBaseUrl() + "/api/states";
        ThirdPartyHttp.Resp r;
        try {
            r = ThirdPartyHttp.get("homeassistant", url, headers(token(cfg)), TIMEOUT_MS);
        } catch (IOException e) {
            markSync(cfg, null, "连不上 Home Assistant");
            return;
        }
        if (!r.ok()) {
            markSync(cfg, null, httpMessage(r.status()));
            return;
        }
        try {
            JsonNode arr = json.readTree(r.body());
            if (!arr.isArray()) throw new IllegalStateException("states is not an array");
            int n = applyStates(cfg, arr);
            markSync(cfg, LocalDateTime.now(), null);
            log.debug("iot sync done, familyId={}, devices={}", cfg.getFamilyId(), n);
        } catch (Exception e) {
            log.warn("iot sync failed, familyId={}", cfg.getFamilyId(), e);
            markSync(cfg, null, "读取设备状态失败");
        }
    }

    /** 全量实体 → 设备行:新增/回写变更,按采样规则写历史。返回本次有变动的设备数。 */
    private int applyStates(IotConfig cfg, JsonNode arr) {
        Map<String, IotDevice> byEntity = new LinkedHashMap<>();
        for (IotDevice d : deviceMapper.selectList(new LambdaQueryWrapper<IotDevice>()
                .eq(IotDevice::getFamilyId, cfg.getFamilyId()))) {
            byEntity.put(d.getEntityId(), d);
        }
        LocalDateTime now = LocalDateTime.now();
        int touched = 0;
        for (JsonNode st : arr) {
            String entityId = text(st, "entity_id");
            if (entityId == null || entityId.indexOf('.') <= 0) continue;
            String domain = entityId.substring(0, entityId.indexOf('.')).toLowerCase(Locale.ROOT);
            if (!TRACKED.contains(domain)) continue;
            IotDevice d = byEntity.get(entityId);
            if (d == null) {
                d = insertDevice(cfg, st, entityId, domain, now);
                byEntity.put(entityId, d);
                touched++;
                continue;
            }
            if (applyOne(d, st)) touched++;
        }
        return touched;
    }

    /** 回写单个实体;返回是否发生了变更(新增历史也计入)。
     *  显示名只在首次建行时取自 HA,之后归家长改(否则每次同步会把改好的名字盖掉)。 */
    private boolean applyOne(IotDevice d, JsonNode st) {
        LocalDateTime now = LocalDateTime.now();
        String state = truncate(text(st, "state"), MAX_STATE);
        String unit = truncate(text(st.path("attributes"), "unit_of_measurement"), 20);
        String deviceClass = truncate(text(st.path("attributes"), "device_class"), 40);
        LocalDateTime seen = lastUpdated(st);

        boolean stateChanged = !Objects.equals(state, d.getState());
        boolean metaChanged = !Objects.equals(unit, d.getUnit())
                || !Objects.equals(deviceClass, d.getDeviceClass());
        boolean seenChanged = seen != null && !seen.equals(d.getLastSeenAt());
        boolean due = d.getLastSampleAt() == null
                || Duration.between(d.getLastSampleAt(), now).compareTo(SAMPLE_INTERVAL) >= 0;
        boolean sample = stateChanged || (HEARTBEAT.contains(d.getDomain()) && due);
        if (!stateChanged && !metaChanged && !seenChanged && !sample) return false;

        d.setState(state);
        d.setUnit(unit);
        d.setDeviceClass(deviceClass);
        if (seen != null) d.setLastSeenAt(seen);
        if (sample) d.setLastSampleAt(now);
        // UPDATE 只 SET 业务字段,保住 updated_at 的 ON UPDATE CURRENT_TIMESTAMP(见踩坑速查 §2.2)
        deviceMapper.update(null, new LambdaUpdateWrapper<IotDevice>()
                .eq(IotDevice::getId, d.getId())
                .set(IotDevice::getState, d.getState())
                .set(IotDevice::getUnit, d.getUnit())
                .set(IotDevice::getDeviceClass, d.getDeviceClass())
                .set(IotDevice::getLastSeenAt, d.getLastSeenAt())
                .set(IotDevice::getLastSampleAt, d.getLastSampleAt()));
        if (sample) insertSample(d);
        return true;
    }

    private IotDevice insertDevice(IotConfig cfg, JsonNode st, String entityId, String domain, LocalDateTime now) {
        IotDevice d = new IotDevice();
        d.setFamilyId(cfg.getFamilyId());
        d.setEntityId(entityId);
        d.setDomain(domain);
        d.setName(truncate(friendlyName(st, entityId), MAX_NAME));
        d.setUnit(truncate(text(st.path("attributes"), "unit_of_measurement"), 20));
        d.setDeviceClass(truncate(text(st.path("attributes"), "device_class"), 40));
        d.setState(truncate(text(st, "state"), MAX_STATE));
        d.setEnabled(1);
        d.setLastSeenAt(lastUpdated(st));
        d.setLastSampleAt(now);
        deviceMapper.insert(d);
        insertSample(d);
        return d;
    }

    private void insertSample(IotDevice d) {
        IotData row = new IotData();
        row.setDeviceId(d.getId());
        row.setValue(d.getState() == null ? "" : d.getState());
        dataMapper.insert(row);
    }

    private void markSync(IotConfig cfg, LocalDateTime at, String error) {
        configMapper.update(null, new LambdaUpdateWrapper<IotConfig>()
                .eq(IotConfig::getId, cfg.getId())
                .set(IotConfig::getLastSyncAt, at)
                .set(IotConfig::getLastError, error));
    }

    // ==================== 内部 ====================

    private Map<String, Object> view(IotDevice d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("entityId", d.getEntityId());
        m.put("name", d.getName());
        m.put("domain", d.getDomain());
        m.put("deviceClass", d.getDeviceClass());
        m.put("unit", d.getUnit());
        m.put("room", d.getRoom());
        m.put("state", d.getState());
        m.put("enabled", d.getEnabled() == null || d.getEnabled() == 1);
        m.put("controllable", CONTROLLABLE.contains(d.getDomain()));
        m.put("lastSeenAt", d.getLastSeenAt());
        return m;
    }

    private IotConfig find(Long familyId) {
        if (familyId == null) return null;
        return configMapper.selectOne(new LambdaQueryWrapper<IotConfig>()
                .eq(IotConfig::getFamilyId, familyId).last("limit 1"));
    }

    private IotConfig requireReady(Long familyId) {
        IotConfig row = find(familyId);
        if (row == null) throw new BizException(ResultCode.BAD_REQUEST, "还没接入智能家居");
        if (row.getEnabled() != null && row.getEnabled() == 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "智能家居接入已停用");
        }
        return row;
    }

    private IotDevice requireDevice(Long familyId, Long deviceId, String entityId) {
        if (familyId == null) throw new BizException(ResultCode.NOT_FOUND, "设备不存在");
        LambdaQueryWrapper<IotDevice> qw = new LambdaQueryWrapper<IotDevice>()
                .eq(IotDevice::getFamilyId, familyId);
        if (deviceId != null) qw.eq(IotDevice::getId, deviceId);
        if (entityId != null) qw.eq(IotDevice::getEntityId, entityId);
        IotDevice d = deviceMapper.selectOne(qw.last("limit 1"));
        if (d == null) throw new BizException(ResultCode.NOT_FOUND, "设备不存在");
        return d;
    }

    private String token(IotConfig cfg) {
        return parameterService.decrypt(cfg.getToken());
    }

    private static Map<String, String> headers(String token) {
        if (!notBlank(token)) return Map.of();
        return Map.of("Authorization", "Bearer " + token, "Content-Type", "application/json");
    }

    private static Map<String, String> bearer(String token) {
        return notBlank(token) ? Map.of("Authorization", "Bearer " + token) : Map.of();
    }

    private static String friendlyName(JsonNode st, String entityId) {
        String name = text(st.path("attributes"), "friendly_name");
        if (notBlank(name)) return name;
        return entityId.substring(entityId.indexOf('.') + 1).replace('_', ' ').trim();
    }

    private static LocalDateTime lastUpdated(JsonNode st) {
        String s = text(st, "last_updated");
        if (!notBlank(s)) s = text(st, "last_changed");
        if (!notBlank(s)) return null;
        try {
            return LocalDateTime.ofInstant(OffsetDateTime.parse(s).toInstant(), ZoneId.systemDefault());
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(s);
            } catch (Exception e2) {
                return null;
            }
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node == null ? null : node.get(field);
        return v == null || v.isNull() ? null : v.asText(null);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        String v = s.trim();
        return v.length() <= max ? v : v.substring(0, max);
    }

    private static String httpMessage(int status) {
        if (status == 401 || status == 403) return "令牌无效或已过期,请重新获取";
        if (status == 404) return "地址不对,找不到 Home Assistant";
        if (status >= 500) return "Home Assistant 暂时不可用,请稍后再试";
        return "请求失败,请稍后再试";
    }
}
