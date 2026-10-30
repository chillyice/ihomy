package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.AnniversaryDate;
import com.ihomy.entity.Anniversary;
import com.ihomy.entity.Notification;
import com.ihomy.mapper.AnniversaryMapper;
import com.ihomy.mapper.NotificationMapper;
import com.ihomy.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * 纪念日提前提醒:每 5 分钟扫描「提前 N 天提醒」的纪念日,到点给全家发站内通知。
 * 日期口径与首页倒计时一致(AnniversaryDate);同一条纪念日当天只通知一次。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnniversaryReminderService {

    /** 未填提醒时刻时按早上 8 点发 */
    private static final LocalTime DEFAULT_REMIND_TIME = LocalTime.of(8, 0);

    private final AnniversaryMapper anniversaryMapper;
    private final SysUserMapper sysUserMapper;
    private final NotificationMapper notificationMapper;
    private final NotificationService notificationService;

    /** 每 5 分钟扫描一次:距纪念日恰好 N 天且已到提醒时刻时,给全家成员发站内通知 */
    @Scheduled(fixedDelay = 300_000)
    public void processDue() {
        LocalTime now = LocalTime.now();
        if (now.isBefore(LocalTime.of(6, 0)) || now.isAfter(LocalTime.of(23, 0))) {
            return; // 夜间静默,避免打扰
        }
        LocalDate today = LocalDate.now();
        List<Anniversary> list = anniversaryMapper.selectList(new LambdaQueryWrapper<Anniversary>()
                .gt(Anniversary::getRemindDaysBefore, 0));
        for (Anniversary a : list) {
            try {
                if (a.getFamilyId() == null) continue;
                LocalDate next = AnniversaryDate.nextOccurrence(a.getCalendar(), a.getMonth(), a.getDay(),
                        a.getIsLeap(), today);
                if (next == null) continue;
                long days = ChronoUnit.DAYS.between(today, next);
                if (days != a.getRemindDaysBefore()) continue;
                LocalTime remindAt = a.getRemindTime() == null ? DEFAULT_REMIND_TIME : a.getRemindTime();
                if (remindAt.isAfter(now.plusMinutes(5))) continue; // 还没到点
                if (notifiedToday(a)) continue;                     // 当天已发过
                List<Map<String, Object>> members = sysUserMapper.selectMembersByFamily(a.getFamilyId());
                for (Map<String, Object> m : members) {
                    notificationService.create((Long) m.get("id"), "anniversary",
                            "纪念日提醒：" + a.getName() + "（" + days + " 天后）",
                            a.getId(), "anniversary", a.getId());
                }
            } catch (Exception e) {
                // 单条纪念日失败不影响其他纪念日
                log.error("anniversary notify failed, anniversaryId={}", a.getId(), e);
            }
        }
    }

    /** 当天是否已为该纪念日发过通知(家人共用一条来源记录,按家庭单位查重) */
    private boolean notifiedToday(Anniversary a) {
        Long count = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getType, "anniversary")
                .eq(Notification::getSourceId, a.getId())
                .ge(Notification::getCreatedAt, LocalDate.now().atStartOfDay()));
        return count != null && count > 0;
    }
}
