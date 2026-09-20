package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.entity.Item;
import com.ihomy.entity.Notification;
import com.ihomy.mapper.ItemMapper;
import com.ihomy.mapper.NotificationMapper;
import com.ihomy.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 食材过期预警:定时扫描有保质期的食材,按三档分级(红=已过期/黄=临期/绿=健康)汇总,
 * 红/黄食材每家庭每天给全家成员发一条站内汇总通知(全家可看)。
 * 分级规则(与前端 Ingredient.vue 保持一致):过期=红;总时长≤3 天或剩余≤10%=黄;其余=绿。
 */
@Service
@RequiredArgsConstructor
public class ItemExpiryService {

    private final ItemMapper itemMapper;
    private final NotificationMapper notificationMapper;
    private final NotificationService notificationService;
    private final SysUserMapper sysUserMapper;

    /** 每 5 分钟扫描一次,夜间(23:00-6:00)静默;每家庭每天一条汇总 */
    @Scheduled(fixedDelay = 300_000)
    public void processExpiryWarnings() {
        LocalTime now = LocalTime.now();
        if (now.isBefore(LocalTime.of(6, 0)) || now.isAfter(LocalTime.of(23, 0))) {
            return;
        }
        List<Item> ingredients = itemMapper.selectList(new LambdaQueryWrapper<Item>()
                .eq(Item::getType, "INGREDIENT")
                .isNotNull(Item::getStoredAt)
                .isNotNull(Item::getShelfLife)
                .gt(Item::getShelfLife, 0));
        // familyId -> [红数量, 黄数量]
        Map<Long, long[]> counts = new HashMap<>();
        for (Item it : ingredients) {
            if (it.getFamilyId() == null) continue;
            String level = warnLevel(it);
            long[] c = counts.computeIfAbsent(it.getFamilyId(), k -> new long[2]);
            if ("RED".equals(level)) c[0]++;
            else if ("YELLOW".equals(level)) c[1]++;
        }
        for (Map.Entry<Long, long[]> e : counts.entrySet()) {
            long red = e.getValue()[0];
            long yellow = e.getValue()[1];
            if (red == 0 && yellow == 0) continue;
            try {
                if (notifiedToday(e.getKey())) continue;
                String content = buildContent(red, yellow);
                List<Map<String, Object>> members = sysUserMapper.selectMembersByFamily(e.getKey());
                for (Map<String, Object> m : members) {
                    notificationService.create((Long) m.get("id"), "item_expiry", content, e.getKey(), "item", null);
                }
            } catch (Exception ex) {
                // 单个家庭失败不影响其他家庭
            }
        }
    }

    /** 三档预警级别:RED 已过期;YELLOW 总时长≤3天 或 剩余≤10%;GREEN 健康 */
    private String warnLevel(Item it) {
        Duration total = totalDuration(it);
        if (total == null || total.isZero() || total.isNegative()) return "GREEN";
        LocalDateTime expiry = it.getStoredAt().plus(total);
        if (LocalDateTime.now().isAfter(expiry)) return "RED";
        long totalMs = total.toMillis();
        if (totalMs <= 3L * 24 * 60 * 60 * 1000) return "YELLOW";
        long remainingMs = Duration.between(LocalDateTime.now(), expiry).toMillis();
        if (remainingMs <= totalMs / 10) return "YELLOW";
        return "GREEN";
    }

    /** 保质期总时长(小时/天/月统一折算为 Duration;月按 30 天近似) */
    private Duration totalDuration(Item it) {
        if (it.getStoredAt() == null || it.getShelfLife() == null || it.getShelfLife() <= 0) return null;
        long n = it.getShelfLife();
        String unit = it.getShelfLifeUnit() == null ? "DAY" : it.getShelfLifeUnit();
        long hours = switch (unit) {
            case "HOUR" -> n;
            case "MONTH" -> n * 24L * 30L;
            default -> n * 24L;
        };
        return Duration.ofHours(hours);
    }

    private boolean notifiedToday(Long familyId) {
        Long count = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getType, "item_expiry")
                .eq(Notification::getSourceId, familyId)
                .ge(Notification::getCreatedAt, LocalDate.now().atStartOfDay()));
        return count != null && count > 0;
    }

    private String buildContent(long red, long yellow) {
        StringBuilder sb = new StringBuilder("食材过期预警:");
        if (red > 0) sb.append(red).append(" 件已过期");
        if (red > 0 && yellow > 0) sb.append(",");
        if (yellow > 0) sb.append(yellow).append(" 件即将过期");
        sb.append(",请及时处理");
        return sb.toString();
    }
}
