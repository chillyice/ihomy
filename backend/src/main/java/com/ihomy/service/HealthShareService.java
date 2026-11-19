package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.HealthReportCard;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.HealthShareDTO;
import com.ihomy.entity.FamilyHealthShare;
import com.ihomy.entity.SysUser;
import com.ihomy.mapper.FamilyHealthShareMapper;
import com.ihomy.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 健康报表分享:把此刻的报表拍成一份冻结快照(family_health_share),给一条链接。
 * 他人打开链接只看得到这份快照,看不到本人的其他健康数据;7 天到期即 404,
 * 后台每日清理物理删行(HealthRetentionService),本人也可随时手动删除立刻失效。
 * 快照内容生成后不再变化——本人后面删数据、改设备都不影响这 7 天里别人看到的内容。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HealthShareService {

    /** 快照有效期:7 天(产品口径,不做可配置,避免「链接还能看多久」说不清) */
    public static final int SHARE_DAYS = 7;
    private static final int MAX_LIST = 50;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final FamilyHealthShareMapper shareMapper;
    private final SysUserMapper userMapper;
    private final HealthReportService reportService;
    private final HealthPrivacyService privacyService;
    private final ObjectMapper objectMapper;

    /**
     * 生成快照:卡片清单先收口,敏感卡片要同时满足「本次勾选」与「本人已开同意」;
     * 敏感卡片被清掉后为空则回落默认五项。
     */
    @Transactional
    public Map<String, Object> create(Long userId, Long familyId, HealthShareDTO dto) {
        HealthShareDTO input = dto == null ? new HealthShareDTO() : dto;
        boolean includeSensitive = Boolean.TRUE.equals(input.getIncludeSensitive())
                && privacyService.reproductiveConsent(userId);
        List<String> cards = new ArrayList<>();
        for (String id : HealthReportCard.sanitize(input.getCards())) {
            if (HealthReportCard.isSensitive(id) && !includeSensitive) continue;
            cards.add(id);
        }
        if (cards.isEmpty()) {
            cards = new ArrayList<>(HealthReportCard.DEFAULTS);
            includeSensitive = false;
        }

        Map<String, Object> report = reportService.build(userId, cards, includeSensitive, input.getDate());
        LocalDateTime now = LocalDateTime.now();
        FamilyHealthShare row = new FamilyHealthShare();
        row.setFamilyId(familyId);
        row.setOwnerUserId(userId);
        row.setToken(newToken());
        row.setIncludeSensitive(includeSensitive ? 1 : 0);
        row.setAnchorDate(parseDate(report.get("anchorDate")));
        row.setExpireAt(now.plusDays(SHARE_DAYS));
        row.setViewCount(0);
        row.setCreatedAt(now);
        try {
            row.setCards(objectMapper.writeValueAsString(cards));
            row.setSnapshot(objectMapper.writeValueAsString(report));
        } catch (Exception e) {
            log.error("生成健康报表快照失败 uid={}", userId, e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "生成分享链接失败,请稍后重试");
        }
        shareMapper.insert(row);
        return summary(row, parseCards(row.getCards()));
    }

    /** 我生成过的链接(只剩未过期的;剩余天数与查看次数直接给前端展示) */
    public List<Map<String, Object>> list(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        List<FamilyHealthShare> rows = shareMapper.selectList(new LambdaQueryWrapper<FamilyHealthShare>()
                .eq(FamilyHealthShare::getOwnerUserId, userId)
                .gt(FamilyHealthShare::getExpireAt, now)
                .orderByDesc(FamilyHealthShare::getCreatedAt)
                .last("LIMIT " + MAX_LIST));
        List<Map<String, Object>> out = new ArrayList<>();
        for (FamilyHealthShare row : rows) {
            out.add(summary(row, parseCards(row.getCards())));
        }
        return out;
    }

    /** 手动删除:只能删自己生成的,删完链接立刻 404(物理删,快照内容一并消失) */
    public void delete(Long userId, Long id) {
        FamilyHealthShare row = id == null ? null : shareMapper.selectById(id);
        if (row == null || !userId.equals(row.getOwnerUserId())) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        shareMapper.deleteById(id);
    }

    /**
     * 打开分享链接(免登录):只回冻结快照,不再查业务表。
     * 过期直接删行 + 404;有效则累加查看次数并写一条 scope=SHARE 审计(viewer 为 NULL)。
     */
    public Map<String, Object> open(String token, String viewerIp) {
        String key = token == null ? "" : token.trim();
        FamilyHealthShare row = key.isEmpty() ? null : shareMapper.selectOne(
                new LambdaQueryWrapper<FamilyHealthShare>().eq(FamilyHealthShare::getToken, key));
        if (row == null) throw new BizException(ResultCode.NOT_FOUND);
        LocalDateTime now = LocalDateTime.now();
        if (row.getExpireAt() == null || !row.getExpireAt().isAfter(now)) {
            shareMapper.deleteById(row.getId());
            throw new BizException(ResultCode.NOT_FOUND);
        }

        Map<String, Object> report;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(row.getSnapshot(), Map.class);
            report = parsed;
        } catch (Exception e) {
            log.error("解析健康报表快照失败 shareId={}", row.getId(), e);
            throw new BizException(ResultCode.NOT_FOUND);
        }

        shareMapper.update(null, new LambdaUpdateWrapper<FamilyHealthShare>()
                .eq(FamilyHealthShare::getId, row.getId())
                .setSql("view_count = view_count + 1")
                .set(FamilyHealthShare::getLastViewedAt, now));
        // 匿名访问:viewer_id 为 NULL,只留 IP,便于本人在「谁看过」里分辨
        privacyService.logView(null, viewerIp, row.getOwnerUserId(), row.getFamilyId(),
                "SHARE", null, null, null);

        SysUser owner = userMapper.selectById(row.getOwnerUserId());
        Map<String, Object> share = new LinkedHashMap<>();
        share.put("ownerName", owner == null ? null
                : owner.getNickname() != null && !owner.getNickname().isBlank() ? owner.getNickname() : owner.getUsername());
        share.put("ownerAvatar", owner == null ? null : owner.getAvatar());
        share.put("cards", parseCards(row.getCards()));
        share.put("includeSensitive", Integer.valueOf(1).equals(row.getIncludeSensitive()));
        share.put("createdAt", row.getCreatedAt());
        share.put("expireAt", row.getExpireAt());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("report", report);
        data.put("share", share);
        return data;
    }

    /** 每日清理到期快照(由 HealthRetentionService 在 04:20 一并调用) */
    public int purgeExpired(LocalDateTime now) {
        try {
            int n = shareMapper.delete(new LambdaQueryWrapper<FamilyHealthShare>()
                    .lt(FamilyHealthShare::getExpireAt, now));
            if (n > 0) {
                log.info("清理到期健康报表分享 {} 条", n);
            }
            return n;
        } catch (Exception e) {
            log.warn("清理到期健康报表分享失败: {}", e.getMessage());
            return 0;
        }
    }

    private Map<String, Object> summary(FamilyHealthShare row, List<String> cards) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", row.getId());
        item.put("token", row.getToken());
        item.put("path", "/health/shared/" + row.getToken());
        item.put("cards", cards);
        item.put("includeSensitive", Integer.valueOf(1).equals(row.getIncludeSensitive()));
        item.put("anchorDate", row.getAnchorDate());
        item.put("createdAt", row.getCreatedAt());
        item.put("expireAt", row.getExpireAt());
        item.put("remainingDays", remainingDays(row.getExpireAt()));
        item.put("viewCount", row.getViewCount() == null ? 0 : row.getViewCount());
        item.put("lastViewedAt", row.getLastViewedAt());
        return item;
    }

    /** 剩余天数向上取整:还剩几小时也显示「1 天」,避免「0 天」但还能打开 */
    private long remainingDays(LocalDateTime expireAt) {
        if (expireAt == null) return 0;
        long minutes = ChronoUnit.MINUTES.between(LocalDateTime.now(), expireAt);
        return minutes <= 0 ? 0 : (minutes + 1439) / 1440;
    }

    @SuppressWarnings("unchecked")
    private List<String> parseCards(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>(HealthReportCard.DEFAULTS);
        try {
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            log.warn("解析健康分享卡片清单失败: {}", e.getMessage());
            return new ArrayList<>(HealthReportCard.DEFAULTS);
        }
    }

    private LocalDate parseDate(Object value) {
        if (value instanceof LocalDate d) return d;
        if (value instanceof CharSequence s && !s.toString().isBlank()) {
            try {
                return LocalDate.parse(s.toString());
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    /** 32 位 hex 令牌:持有即可查看,不可枚举(16 字节 SecureRandom) */
    private String newToken() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
