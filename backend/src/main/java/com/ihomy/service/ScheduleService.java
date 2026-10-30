package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.AnniversaryDate;
import com.ihomy.common.BizException;
import com.ihomy.common.DictConst;
import com.ihomy.common.RepeatSchedule;
import com.ihomy.common.ResultCode;
import com.ihomy.entity.FamilyPlan;
import com.ihomy.entity.PlanTask;
import com.ihomy.entity.Reminder;
import com.ihomy.entity.ReminderCheck;
import com.ihomy.mapper.FamilyPlanMapper;
import com.ihomy.mapper.FamilyPlanTaskMapper;
import com.ihomy.mapper.ReminderCheckMapper;
import com.ihomy.mapper.ReminderMapper;
import com.ihomy.service.schedule.ScheduleSourceProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 日程聚合读服务:把纪念日/提醒/每日任务/计划/计划子任务按日期摊平,供月历与近期提醒使用。
 * <p>
 * 写侧仍落各自既有表与接口,本服务只读;日期算法不另写一套:
 * 提醒与每日任务逐日判定走 {@link RepeatSchedule},纪念日展开走 {@link AnniversaryDate},
 * 计划按 target_date、子任务按 due_date 取区间。
 * 贷款还款日/理财到期/食材到期等「别处已存在」的数据走 {@link ScheduleSourceProvider} 实现列表汇总,
 * 新增来源只加实现类,不在本服务里堆 if-else。
 * 游客口径(anniversaryOnly=true)只返回纪念日,其余为空,且不发需要登录的查询。
 */
@Service
@RequiredArgsConstructor
public class ScheduleService {

    /** 区间查询上限(含首尾),防止误传整年 */
    private static final int MAX_RANGE_DAYS = 62;
    /** 近期提醒默认天数与上限 */
    private static final int DEFAULT_UPCOMING_DAYS = 7;
    private static final int MAX_UPCOMING_DAYS = 30;
    /** 近期提醒最多返回条数 */
    private static final int UPCOMING_LIMIT = 20;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final ReminderMapper reminderMapper;
    private final ReminderCheckMapper reminderCheckMapper;
    private final FamilyPlanMapper planMapper;
    private final FamilyPlanTaskMapper planTaskMapper;
    private final AnniversaryService anniversaryService;
    /** 只读来源实现列表(贷款/理财/食材),顺序由各实现的 @Order 决定 */
    private final List<ScheduleSourceProvider> sourceProviders;

    /**
     * 区间日程聚合。
     *
     * @param familyId        可见家庭(可为空,空时行程为空)
     * @param from            起始日(含)
     * @param to              结束日(含),区间最多 62 天
     * @param anniversaryOnly 游客口径:只给纪念日,其余 kind 为空
     * @return { days: { 日期: [事项...] }, undated: [无日期计划...] }
     */
    public Map<String, Object> events(Long familyId, LocalDate from, LocalDate to, boolean anniversaryOnly) {
        validateRange(from, to);
        List<Map<String, Object>> items = collectOccurrences(familyId, from, to, anniversaryOnly);
        Map<String, List<Map<String, Object>>> days = new LinkedHashMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            days.put(d.toString(), new ArrayList<>());
        }
        for (Map<String, Object> item : items) {
            days.get((String) item.get("date")).add(item);
        }
        days.values().forEach(ScheduleService::sortDay);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("days", days);
        data.put("undated", anniversaryOnly ? List.of() : collectUndatedPlans(familyId));
        return data;
    }

    /**
     * 近期日程提醒:未来 N 天(默认 7,上限 30)的事项,按日期升序、同日按时刻,最多 20 条。
     * 循环事项(每日任务/每年提醒)在窗口内只保留最近一次发生,避免逐日刷屏。
     */
    public List<Map<String, Object>> upcoming(Long familyId, Integer days) {
        int span = days == null || days < 1 ? DEFAULT_UPCOMING_DAYS : Math.min(days, MAX_UPCOMING_DAYS);
        LocalDate today = LocalDate.now();
        LocalDate to = today.plusDays(span - 1L);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> item : collectOccurrences(familyId, today, to, false)) {
            if ("PLAN_TASK".equals(item.get("kind"))) continue; // 子任务只在当日详情里出现
            if (Boolean.TRUE.equals(item.get("done"))) continue; // 已完成的不提醒
            items.add(item);
        }
        items.sort(Comparator
                .comparing((Map<String, Object> m) -> (String) m.get("date"))
                .thenComparing(m -> (String) m.get("time"), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(m -> kindRank((String) m.get("kind")))
                .thenComparing(m -> (Long) m.get("id")));
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> recurringSeen = new HashSet<>();
        for (Map<String, Object> item : items) {
            if (isRecurringRepeat((String) item.get("repeat"))) {
                String key = item.get("kind") + ":" + item.get("id");
                if (!recurringSeen.add(key)) continue;
            }
            result.add(item);
            if (result.size() >= UPCOMING_LIMIT) break;
        }
        return result;
    }

    /** 摊平区间内的全部事项(每项带 date),游客口径只摊纪念日 */
    private List<Map<String, Object>> collectOccurrences(Long familyId, LocalDate from, LocalDate to,
                                                        boolean anniversaryOnly) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (familyId == null) return items;
        appendAnniversaries(familyId, from, to, items);
        if (anniversaryOnly) return items;
        appendReminders(familyId, from, to, items);
        appendPlans(familyId, from, to, items);
        appendReadOnlySources(familyId, from, to, items);
        return items;
    }

    /**
     * 只读来源(贷款还款日/理财到期/食材到期):遍历实现列表逐个摊平。
     * 新增来源只加一个 ScheduleSourceProvider 实现,聚合主逻辑不动;不可用(无权限/未登录)时整段跳过。
     */
    private void appendReadOnlySources(Long familyId, LocalDate from, LocalDate to, List<Map<String, Object>> items) {
        for (ScheduleSourceProvider provider : sourceProviders) {
            if (!provider.available(familyId)) continue;
            provider.append(familyId, from, to, items);
        }
    }

    /** 纪念日:按 AnniversaryDate 展开到区间内的每年发生日(阳历/农历同一套口径) */
    private void appendAnniversaries(Long familyId, LocalDate from, LocalDate to, List<Map<String, Object>> items) {
        LocalDate today = LocalDate.now();
        for (Map<String, Object> a : anniversaryService.list(familyId)) {
            if (!DictConst.RECUR_YEARLY.equals(a.get("recurring"))) continue;
            String calendar = (String) a.get("calendar");
            Integer month = intValue(a.get("month"));
            Integer day = intValue(a.get("day"));
            Integer isLeap = intValue(a.get("isLeap"));
            LocalDate cursor = from;
            while (!cursor.isAfter(to)) {
                LocalDate occurrence = AnniversaryDate.nextOccurrence(calendar, month, day, isLeap, cursor);
                if (occurrence == null || occurrence.isAfter(to)) break;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("kind", "ANNIVERSARY");
                item.put("id", a.get("id"));
                item.put("title", a.get("name"));
                item.put("date", occurrence.toString());
                item.put("time", null);
                item.put("days", (int) ChronoUnit.DAYS.between(today, occurrence));
                item.put("calendar", calendar);
                item.put("members", a.getOrDefault("memberNames", List.of()));
                items.add(item);
                cursor = occurrence.plusDays(1);
            }
        }
    }

    /**
     * 提醒与每日任务:一次性未完成的按当天落一个,循环事项逐日判定。
     * 计划托管的提醒(planId 非空)跳过:同一天的计划项已带提醒时刻,避免一条日程出现两次。
     */
    private void appendReminders(Long familyId, LocalDate from, LocalDate to, List<Map<String, Object>> items) {
        List<Reminder> reminders = reminderMapper.selectList(new LambdaQueryWrapper<Reminder>()
                .eq(Reminder::getFamilyId, familyId));
        if (reminders.isEmpty()) return;
        List<Long> recurringIds = new ArrayList<>();
        for (Reminder r : reminders) {
            if (ReminderService.isRecurring(r)) recurringIds.add(r.getId());
        }
        Set<String> checked = checkedKeys(recurringIds, from, to);
        for (Reminder r : reminders) {
            if (r.getPlanId() != null) continue;
            boolean recurring = ReminderService.isRecurring(r);
            if (!recurring && r.getDone() != null && r.getDone() == 1) continue;
            String kind = recurring ? "TASK" : "REMINDER";
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                if (!RepeatSchedule.dueToday(r.getRepeatType(), r.getRemindDate(), d,
                        r.getCalendar(), r.getLunarMonth(), r.getLunarDay(), r.getIsLeap())) continue;
                boolean done = recurring && checked.contains(r.getId() + "|" + d);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("kind", kind);
                item.put("id", r.getId());
                item.put("title", r.getTitle());
                item.put("date", d.toString());
                item.put("time", formatTime(r.getRemindTime()));
                item.put("repeat", r.getRepeatType());
                item.put("calendar", r.getCalendar());
                item.put("done", done);
                item.put("doneToday", done && d.equals(LocalDate.now()));
                items.add(item);
            }
        }
    }

    /** 计划(按目标日)与计划子任务(按截止日);计划已取消的不出现 */
    private void appendPlans(Long familyId, LocalDate from, LocalDate to, List<Map<String, Object>> items) {
        List<FamilyPlan> plans = planMapper.selectList(new LambdaQueryWrapper<FamilyPlan>()
                .eq(FamilyPlan::getFamilyId, familyId)
                .ne(FamilyPlan::getStatus, DictConst.PLAN_CANCELLED));
        if (plans.isEmpty()) return;
        List<Long> planIds = new ArrayList<>();
        for (FamilyPlan p : plans) planIds.add(p.getId());
        Map<Long, String> remindTimes = planRemindTimes(planIds);
        for (FamilyPlan p : plans) {
            LocalDate target = p.getTargetDate();
            if (target == null || target.isBefore(from) || target.isAfter(to)) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("kind", "PLAN");
            item.put("id", p.getId());
            item.put("title", p.getTitle());
            item.put("date", target.toString());
            item.put("time", remindTimes.get(p.getId()));
            item.put("status", p.getStatus());
            items.add(item);
        }
        // 子任务一次 in 查询,不按计划逐条查
        for (PlanTask t : planTaskMapper.selectList(new LambdaQueryWrapper<PlanTask>()
                .in(PlanTask::getPlanId, planIds)
                .between(PlanTask::getDueDate, from, to))) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("kind", "PLAN_TASK");
            item.put("id", t.getId());
            item.put("planId", t.getPlanId());
            item.put("title", t.getTitle());
            item.put("date", t.getDueDate().toString());
            item.put("time", null);
            item.put("done", t.getDone() != null && t.getDone() == 1);
            items.add(item);
        }
    }

    /** 无日期计划(待安排):进行中且没有目标日 */
    private List<Map<String, Object>> collectUndatedPlans(Long familyId) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (familyId == null) return result;
        for (FamilyPlan p : planMapper.selectList(new LambdaQueryWrapper<FamilyPlan>()
                .eq(FamilyPlan::getFamilyId, familyId)
                .eq(FamilyPlan::getStatus, DictConst.PLAN_ACTIVE)
                .isNull(FamilyPlan::getTargetDate))) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", p.getId());
            item.put("title", p.getTitle());
            item.put("createdAt", p.getCreatedAt() == null ? null : p.getCreatedAt().toLocalDate().toString());
            result.add(item);
        }
        return result;
    }

    /** 批量取计划关联提醒的时刻(一次查询,同一天多条取最早) */
    private Map<Long, String> planRemindTimes(List<Long> planIds) {
        Map<Long, LocalTime> times = new HashMap<>();
        for (Reminder r : reminderMapper.selectList(new LambdaQueryWrapper<Reminder>()
                .in(Reminder::getPlanId, planIds)
                .orderByAsc(Reminder::getRemindTime))) {
            times.putIfAbsent(r.getPlanId(), r.getRemindTime());
        }
        Map<Long, String> map = new HashMap<>();
        times.forEach((planId, time) -> map.put(planId, formatTime(time)));
        return map;
    }

    /** 循环提醒在区间内的完成记录,键=提醒ID|日期(一次查询) */
    private Set<String> checkedKeys(List<Long> reminderIds, LocalDate from, LocalDate to) {
        if (reminderIds.isEmpty()) return Set.of();
        Set<String> keys = new HashSet<>();
        for (ReminderCheck c : reminderCheckMapper.selectList(new LambdaQueryWrapper<ReminderCheck>()
                .in(ReminderCheck::getReminderId, reminderIds)
                .between(ReminderCheck::getCheckDate, from, to))) {
            keys.add(c.getReminderId() + "|" + c.getCheckDate());
        }
        return keys;
    }

    /** 当日排序:有时刻的按时刻升序在前;全天事项依次为纪念日 / 计划 / 子任务 / 只读来源,同组按 id */
    private static void sortDay(List<Map<String, Object>> items) {
        items.sort(Comparator
                .comparing((Map<String, Object> m) -> (String) m.get("time"), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(m -> kindRank((String) m.get("kind")))
                .thenComparing(m -> (Long) m.get("id")));
    }

    private static int kindRank(String kind) {
        return switch (kind == null ? "" : kind) {
            case "ANNIVERSARY" -> 0;
            case "PLAN" -> 1;
            case "PLAN_TASK" -> 2;
            case "LOAN" -> 3;
            case "FINANCE" -> 4;
            case "ITEM_EXPIRY" -> 5;
            default -> 6;
        };
    }

    private static boolean isRecurringRepeat(String repeat) {
        return repeat != null && !DictConst.REPEAT_ONCE.equals(repeat);
    }

    private static String formatTime(LocalTime time) {
        return time == null ? null : time.format(TIME_FORMAT);
    }

    private static Integer intValue(Object value) {
        return value instanceof Number n ? n.intValue() : null;
    }

    /** 区间必填、顺序合法、最多 62 天 */
    private static void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "请选择开始与结束日期");
        }
        if (to.isBefore(from)) {
            throw new BizException(ResultCode.BAD_REQUEST, "结束日期不能早于开始日期");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw new BizException(ResultCode.BAD_REQUEST, "查询范围最多 62 天");
        }
    }
}
