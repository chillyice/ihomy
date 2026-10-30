package com.ihomy.service;

import cn.hutool.core.date.ChineseDate;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.AnniversaryDate;
import com.ihomy.common.DictConst;
import com.ihomy.entity.Anniversary;
import com.ihomy.entity.FamilyAnniversaryMember;
import com.ihomy.entity.SysUser;
import com.ihomy.mapper.AnniversaryMapper;
import com.ihomy.mapper.FamilyAnniversaryMemberMapper;
import com.ihomy.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private final FamilyAnniversaryMemberMapper anniversaryMemberMapper;

    /** 组装首页统计:成员数 + 未来 3 个纪念日(按剩余天数排序)+ 今日纪念日(无则 null) */
    public Map<String, Object> getStats(Long familyId) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("memberCount", countMembers(familyId));
        // 一次查询 family_anniversary 同表数据,todayEvent + upcomingEvents 共用
        if (familyId != null) {
            LambdaQueryWrapper<Anniversary> qw = new LambdaQueryWrapper<>();
            qw.eq(Anniversary::getFamilyId, familyId);
            List<Anniversary> all = anniversaryMapper.selectList(qw);
            Set<Long> linkedIds = linkedAnniversaryIds(all);
            stats.put("todayEvent", todayEvent(all, linkedIds));
            stats.put("upcomingEvents", upcomingEvents(all, linkedIds));
        } else {
            stats.put("todayEvent", null);
            stats.put("upcomingEvents", new ArrayList<>());
        }
        return stats;
    }

    /** 有关联成员的纪念日ID集合(一次查询,避免逐条判断);关联成员即视为生日/成员纪念日 */
    private Set<Long> linkedAnniversaryIds(List<Anniversary> anniversaries) {
        Set<Long> linked = new HashSet<>();
        if (anniversaries == null || anniversaries.isEmpty()) return linked;
        List<Long> ids = new ArrayList<>(anniversaries.size());
        for (Anniversary a : anniversaries) ids.add(a.getId());
        for (FamilyAnniversaryMember rel : anniversaryMemberMapper.selectList(
                new LambdaQueryWrapper<FamilyAnniversaryMember>()
                        .select(FamilyAnniversaryMember::getAnniversaryId)
                        .in(FamilyAnniversaryMember::getAnniversaryId, ids))) {
            linked.add(rel.getAnniversaryId());
        }
        return linked;
    }

    /** 今天(阳历/农历均可)是否有纪念日:有则返回 {name, type: birthday|anniversary} */
    private Map<String, Object> todayEvent(List<Anniversary> anniversaries, Set<Long> linkedIds) {
        if (anniversaries == null || anniversaries.isEmpty()) return null;
        LocalDate today = LocalDate.now();
        for (Anniversary a : anniversaries) {
            if (isOn(a, today)) {
                Map<String, Object> e = new HashMap<>();
                e.put("name", a.getName());
                e.put("type", linkedIds.contains(a.getId()) ? "birthday" : "anniversary");
                return e;
            }
        }
        return null;
    }

    /** 判断纪念日日期是否落在指定公历日(阳历比月日,农历做转换) */
    private boolean isOn(Anniversary a, LocalDate date) {
        if (a.getMonth() == null || a.getDay() == null) return false;
        if ("lunar".equalsIgnoreCase(a.getCalendar())) {
            boolean leap = a.getIsLeap() != null && a.getIsLeap() == 1;
            try {
                ChineseDate cd = new ChineseDate(date.getYear(), a.getMonth(), a.getDay(), leap);
                return cd.getGregorianYear() == date.getYear()
                        && cd.getGregorianMonth() + 1 == date.getMonthValue()
                        && cd.getGregorianDay() == date.getDayOfMonth();
            } catch (Exception ex) {
                return false;
            }
        }
        return a.getMonth() == date.getMonthValue() && a.getDay() == date.getDayOfMonth();
    }

    /** 有效(未禁用)成员数 */
    private long countMembers(Long familyId) {
        if (familyId == null) return 0;
        LambdaQueryWrapper<SysUser> qw = new LambdaQueryWrapper<>();
        qw.eq(SysUser::getFamilyId, familyId).eq(SysUser::getStatus, DictConst.USER_ACTIVE);
        return sysUserMapper.selectCount(qw);
    }

    /** 本家庭未来最近的纪念日(最多 limit 条):首页统计与今日页签共用这一份口径 */
    public List<Map<String, Object>> upcomingEvents(Long familyId, int limit) {
        if (familyId == null) return new ArrayList<>();
        List<Anniversary> all = anniversaryMapper.selectList(new LambdaQueryWrapper<Anniversary>()
                .eq(Anniversary::getFamilyId, familyId));
        return upcomingEvents(all, linkedAnniversaryIds(all), limit);
    }

    /** 计算每年重复纪念日的下一次日期与剩余天数,关联成员记为生日类型 */
    private List<Map<String, Object>> upcomingEvents(List<Anniversary> anniversaries, Set<Long> linkedIds) {
        return upcomingEvents(anniversaries, linkedIds, EVENT_LIMIT);
    }

    private List<Map<String, Object>> upcomingEvents(List<Anniversary> anniversaries, Set<Long> linkedIds, int limit) {
        List<Map<String, Object>> events = new ArrayList<>();
        if (anniversaries == null || anniversaries.isEmpty()) return events;
        LocalDate today = LocalDate.now();
        for (Anniversary a : anniversaries) {
            if (!DictConst.RECUR_YEARLY.equals(a.getRecurring())) continue;
            LocalDate next = AnniversaryDate.nextOccurrence(a.getCalendar(), a.getMonth(), a.getDay(),
                    a.getIsLeap(), today);
            if (next == null) continue;
            Map<String, Object> e = new HashMap<>();
            e.put("type", linkedIds.contains(a.getId()) ? "birthday" : "anniversary");
            e.put("label", a.getName());
            e.put("calendar", a.getCalendar());
            e.put("date", next.toString());
            e.put("days", (int) ChronoUnit.DAYS.between(today, next));
            events.add(e);
        }

        events.sort(Comparator.comparingInt(e -> (int) e.get("days")));
        return events.size() > limit ? new ArrayList<>(events.subList(0, limit)) : events;
    }
}
