package com.ihomy.service;

import cn.hutool.core.date.ChineseDate;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.entity.Anniversary;
import com.ihomy.entity.SysUser;
import com.ihomy.mapper.AnniversaryMapper;
import com.ihomy.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 首页统计:家庭成员数 + 最近纪念日倒计时。
 * 农历转公历基于 Hutool ChineseDate(注意其 getGregorianMonth() 为 0-based,需 +1)。
 */
@Service
@RequiredArgsConstructor
public class HomeStatsService {

    private static final int EVENT_LIMIT = 3;

    private final SysUserMapper sysUserMapper;
    private final AnniversaryMapper anniversaryMapper;

    /** 组装首页统计:成员数 + 未来 3 个纪念日(按剩余天数排序) */
    public Map<String, Object> getStats(Long familyId) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("memberCount", countMembers(familyId));
        stats.put("upcomingEvents", upcomingEvents(familyId));
        return stats;
    }

    /** 有效(未禁用)成员数 */
    private long countMembers(Long familyId) {
        if (familyId == null) return 0;
        LambdaQueryWrapper<SysUser> qw = new LambdaQueryWrapper<>();
        qw.eq(SysUser::getFamilyId, familyId).eq(SysUser::getStatus, 0);
        return sysUserMapper.selectCount(qw);
    }

    /** 计算每年重复纪念日的下一次日期与剩余天数,关联成员记为生日类型 */
    private List<Map<String, Object>> upcomingEvents(Long familyId) {
        List<Map<String, Object>> events = new ArrayList<>();
        if (familyId == null) return events;

        LambdaQueryWrapper<Anniversary> qw = new LambdaQueryWrapper<>();
        qw.eq(Anniversary::getFamilyId, familyId).eq(Anniversary::getRecurring, 1);
        LocalDate today = LocalDate.now();
        for (Anniversary a : anniversaryMapper.selectList(qw)) {
            LocalDate next = nextOccurrence(a, today);
            if (next == null) continue;
            Map<String, Object> e = new HashMap<>();
            e.put("type", a.getUserId() != null ? "birthday" : "anniversary");
            e.put("label", a.getName());
            e.put("calendar", a.getCalendar());
            e.put("date", next.toString());
            e.put("days", (int) ChronoUnit.DAYS.between(today, next));
            events.add(e);
        }

        events.sort(Comparator.comparingInt(e -> (int) e.get("days")));
        return events.size() > EVENT_LIMIT ? events.subList(0, EVENT_LIMIT) : events;
    }

    /** 按历法分支计算下一次日期:阳历直算,农历走转换 */
    private LocalDate nextOccurrence(Anniversary a, LocalDate today) {
        if ("lunar".equalsIgnoreCase(a.getCalendar())) {
            return nextLunar(a, today);
        }
        return nextSolar(a, today);
    }

    /** 阳历日期:今年已过则顺延到下一年 */
    private LocalDate nextSolar(Anniversary a, LocalDate today) {
        if (a.getMonth() == null || a.getDay() == null) return null;
        LocalDate candidate;
        try {
            candidate = LocalDate.of(today.getYear(), a.getMonth(), a.getDay());
        } catch (DateTimeException ex) {
            return null;
        }
        if (candidate.isBefore(today)) {
            candidate = candidate.plusYears(1);
        }
        return candidate;
    }

    /** 农历日期:今年与明年各尝试一次,取首个不早于今天的公历日期;闰月无此年则跳过 */
    private LocalDate nextLunar(Anniversary a, LocalDate today) {
        if (a.getMonth() == null || a.getDay() == null) return null;
        boolean leap = a.getIsLeap() != null && a.getIsLeap() == 1;
        for (int lunarYear = today.getYear(); lunarYear <= today.getYear() + 1; lunarYear++) {
            try {
                ChineseDate cd = new ChineseDate(lunarYear, a.getMonth(), a.getDay(), leap);
                LocalDate solar = LocalDate.of(cd.getGregorianYear(), cd.getGregorianMonth() + 1, cd.getGregorianDay());
                if (!solar.isBefore(today)) {
                    return solar;
                }
            } catch (Exception ex) {
                // 该农历年无此闰月或日期非法,尝试下一年
            }
        }
        return null;
    }
}
