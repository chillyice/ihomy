package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.HealthMetric;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.HealthConsentDTO;
import com.ihomy.entity.FamilyHealthSample;
import com.ihomy.entity.FamilyHealthViewLog;
import com.ihomy.entity.SysUser;
import com.ihomy.entity.SysUserPref;
import com.ihomy.mapper.FamilyHealthSampleMapper;
import com.ihomy.mapper.FamilyHealthSleepMapper;
import com.ihomy.mapper.FamilyHealthSleepStageMapper;
import com.ihomy.mapper.FamilyHealthViewLogMapper;
import com.ihomy.mapper.SysUserMapper;
import com.ihomy.mapper.SysUserPrefMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 健康数据隐私侧:敏感指标同意开关(server-authoritative)、查看审计(轻量 90 天)、按来源清除。
 * 同意开关存 sys_user_pref 键 health.consent,不走 /user-pref 的推拉同步——
 * 隐私授权必须服务端为准,不能由某台设备的 localStorage 说了算。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HealthPrivacyService {

    /** 敏感指标(月经周期类)同意开关的偏好键 */
    static final String CONSENT_PREF_KEY = "health.consent";

    private final SysUserPrefMapper userPrefMapper;
    private final SysUserMapper userMapper;
    private final FamilyHealthSampleMapper sampleMapper;
    private final FamilyHealthSleepMapper sleepMapper;
    private final FamilyHealthSleepStageMapper stageMapper;
    private final FamilyHealthViewLogMapper viewLogMapper;
    private final ObjectMapper objectMapper;

    /** 成员是否已开启生殖健康类敏感指标采集(默认关) */
    public boolean reproductiveConsent(Long userId) {
        SysUserPref row = userPrefMapper.selectOne(new LambdaQueryWrapper<SysUserPref>()
                .eq(SysUserPref::getUserId, userId)
                .eq(SysUserPref::getPrefKey, CONSENT_PREF_KEY)
                .last("LIMIT 1"));
        if (row == null || row.getPrefValue() == null) return false;
        try {
            JsonNode node = objectMapper.readTree(row.getPrefValue());
            JsonNode flag = node.get("reproductive");
            return flag != null && flag.asBoolean();
        } catch (Exception e) {
            log.warn("解析健康同意开关失败 uid={}", userId, e);
            return false;
        }
    }

    public Map<String, Object> consent(Long userId) {
        Map<String, Object> data = new HashMap<>();
        data.put("reproductive", reproductiveConsent(userId));
        return data;
    }

    /** 写同意开关;关闭时可一并清除已采集的敏感指标(默认勾选) */
    @Transactional
    public Map<String, Object> saveConsent(Long userId, HealthConsentDTO dto) {
        boolean on = Boolean.TRUE.equals(dto.getReproductive());
        boolean clear = dto.getClearData() == null || Boolean.TRUE.equals(dto.getClearData());
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("reproductive", on);
        try {
            userPrefMapper.upsert(userId, CONSENT_PREF_KEY, objectMapper.writeValueAsString(value));
        } catch (Exception e) {
            log.warn("保存健康同意开关失败 uid={}", userId, e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "保存健康隐私设置失败,请稍后重试");
        }
        int removed = 0;
        if (!on && clear) {
            removed = sampleMapper.deleteByMetric(userId, HealthMetric.MENSTRUAL_FLOW);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("reproductive", on);
        data.put("removed", removed);
        return data;
    }

    /**
     * 写一条查看审计:现在只在「有人打开分享快照链接」时调用。
     * 本人读自己不记(自读刷屏没意义);分享链接匿名访问 viewerId 传 null,只留 IP 与 scope=SHARE。
     * 审计失败只记 WARN——不能因为记不上日志就把读接口拖死。
     */
    public void logView(Long viewerId, String viewerIp, Long targetUserId, Long familyId,
                        String scope, String metric, java.time.LocalDate from, java.time.LocalDate to) {
        if (targetUserId == null) return;
        if (viewerId != null && Objects.equals(viewerId, targetUserId)) return;
        try {
            FamilyHealthViewLog row = new FamilyHealthViewLog();
            row.setFamilyId(familyId);
            row.setViewerId(viewerId);
            row.setTargetUserId(targetUserId);
            row.setScope(scope);
            row.setMetric(metric);
            row.setFromDate(from);
            row.setToDate(to);
            row.setViewerIp(viewerIp == null ? null : viewerIp.substring(0, Math.min(viewerIp.length(), 45)));
            viewLogMapper.insert(row);
        } catch (Exception e) {
            log.warn("写健康查看审计失败 viewer={} target={} scope={}", viewerId, targetUserId, scope, e);
        }
    }

    /**
     * 「谁看过我分享的健康报表」:只看自己被访问的记录(健康数据本人独占,没有家长视角)。
     * 只展示访问者/范围/时间/IP,不展示快照里的具体数值。
     */
    public List<Map<String, Object>> viewLogs(Long userId, int days) {
        int window = Math.max(1, Math.min(days, 365));
        List<FamilyHealthViewLog> rows = viewLogMapper.selectList(
                new LambdaQueryWrapper<FamilyHealthViewLog>()
                        .eq(FamilyHealthViewLog::getTargetUserId, userId)
                        .ge(FamilyHealthViewLog::getCreatedAt, LocalDateTime.now().minusDays(window))
                        .orderByDesc(FamilyHealthViewLog::getCreatedAt)
                        .last("LIMIT 200"));
        Map<Long, String> names = userNames(rows.stream().map(FamilyHealthViewLog::getViewerId).toList());
        List<Map<String, Object>> out = new ArrayList<>();
        for (FamilyHealthViewLog r : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", r.getId());
            item.put("viewerId", r.getViewerId());
            item.put("viewerName", names.get(r.getViewerId()));
            item.put("anonymous", r.getViewerId() == null);
            item.put("viewerIp", r.getViewerIp());
            item.put("scope", r.getScope());
            item.put("metric", r.getMetric());
            item.put("fromDate", r.getFromDate());
            item.put("toDate", r.getToDate());
            item.put("createdAt", r.getCreatedAt());
            out.add(item);
        }
        return out;
    }

    /** 清除自己某来源(provider 或 source 码)的全部健康数据:换设备/误配用 */
    @Transactional
    public Map<String, Object> clearSource(Long userId, String source) {
        String s = source == null ? "" : source.trim().toUpperCase();
        if (s.isEmpty()) throw new BizException(ResultCode.BAD_REQUEST, "请指定要清除的来源");
        // 先删分期再删会话(分期按 sleep_id 子查询定位,父行删掉后就找不到了)
        int stages = stageMapper.deleteByProvider(userId, s);
        int sleeps = sleepMapper.deleteByProvider(userId, s);
        int samples = sampleMapper.deleteByProvider(userId, s);
        Map<String, Object> data = new HashMap<>();
        data.put("stages", stages);
        data.put("sleeps", sleeps);
        data.put("samples", samples);
        return data;
    }

    private Map<Long, String> userNames(List<Long> ids) {
        List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        Map<Long, String> names = new HashMap<>();
        if (distinct.isEmpty()) return names;
        for (SysUser u : userMapper.selectBatchIds(distinct)) {
            names.put(u.getId(), u.getNickname() != null && !u.getNickname().isBlank() ? u.getNickname() : u.getUsername());
        }
        return names;
    }
}
