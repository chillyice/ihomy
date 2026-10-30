package com.ihomy.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ihomy.common.BizException;
import com.ihomy.common.DictConst;
import com.ihomy.entity.FamilyPlan;
import com.ihomy.entity.PlanTask;
import com.ihomy.entity.Reminder;
import com.ihomy.entity.ReminderCheck;
import com.ihomy.mapper.FamilyPlanMapper;
import com.ihomy.mapper.FamilyPlanTaskMapper;
import com.ihomy.mapper.ReminderCheckMapper;
import com.ihomy.mapper.ReminderMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 日程聚合读口径:纪念日展开、提醒逐日展开、计划与子任务、待安排、
 * 区间上限与游客只读纪念日。纯逻辑断言(不起 Spring 上下文)。
 */
class ScheduleServiceTest {

    private static final Long FAMILY = 2L;

    private final ReminderMapper reminderMapper = mock(ReminderMapper.class);
    private final ReminderCheckMapper checkMapper = mock(ReminderCheckMapper.class);
    private final FamilyPlanMapper planMapper = mock(FamilyPlanMapper.class);
    private final FamilyPlanTaskMapper planTaskMapper = mock(FamilyPlanTaskMapper.class);
    private final AnniversaryService anniversaryService = mock(AnniversaryService.class);
    private final ScheduleService service = new ScheduleService(
            reminderMapper, checkMapper, planMapper, planTaskMapper, anniversaryService);

    /** 单测没有 MyBatis-Plus 启动扫描,手动注册实体信息,否则 LambdaWrapper 的列名解析会报错 */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Reminder.class);
        TableInfoHelper.initTableInfo(assistant, ReminderCheck.class);
        TableInfoHelper.initTableInfo(assistant, FamilyPlan.class);
        TableInfoHelper.initTableInfo(assistant, PlanTask.class);
    }

    // ---------------------------------------------------------------- 工具

    private Reminder reminder(Long id, String repeatType, LocalDate date, LocalTime time, int done) {
        Reminder r = new Reminder();
        r.setId(id);
        r.setFamilyId(FAMILY);
        r.setTitle("事项" + id);
        r.setRemindDate(date);
        r.setRemindTime(time);
        r.setRepeatType(repeatType);
        r.setCalendar(DictConst.CALENDAR_SOLAR);
        r.setIsLeap(0);
        r.setDone(done);
        return r;
    }

    private Map<String, Object> anniversary(long id, String calendar, int month, int day, int isLeap) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("name", "纪念日" + id);
        m.put("calendar", calendar);
        m.put("month", month);
        m.put("day", day);
        m.put("isLeap", isLeap);
        m.put("recurring", DictConst.RECUR_YEARLY);
        m.put("memberNames", List.of("妈妈"));
        return m;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> day(Map<String, Object> result, String date) {
        return ((Map<String, List<Map<String, Object>>>) result.get("days")).get(date);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> undated(Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("undated");
    }

    private FamilyPlan plan(Long id, String title, LocalDate targetDate, String status) {
        FamilyPlan p = new FamilyPlan();
        p.setId(id);
        p.setFamilyId(FAMILY);
        p.setTitle(title);
        p.setTargetDate(targetDate);
        p.setStatus(status);
        p.setCreatedAt(LocalDateTime.of(2026, 10, 12, 9, 0));
        return p;
    }

    // ---------------------------------------------------------------- 区间校验

    @Test
    void rangeLongerThan62DaysRejected() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        assertThatThrownBy(() -> service.events(FAMILY, from, from.plusDays(62), false))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("62");
        // 62 天(含首尾)刚好允许
        assertThat(service.events(FAMILY, from, from.plusDays(61), false)).containsKey("days");
    }

    @Test
    void reversedRangeRejected() {
        LocalDate from = LocalDate.of(2026, 10, 31);
        assertThatThrownBy(() -> service.events(FAMILY, from, from.minusDays(1), false))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("结束日期");
    }

    // ---------------------------------------------------------------- 纪念日

    @Test
    void anniversaryExpandsIntoSolarMonthDay() {
        when(anniversaryService.list(FAMILY)).thenReturn(List.of(anniversary(1, "solar", 10, 30, 0)));

        Map<String, Object> result = service.events(FAMILY,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), false);

        List<Map<String, Object>> items = day(result, "2026-10-30");
        assertThat(items).hasSize(1);
        assertThat(items.get(0)).containsEntry("kind", "ANNIVERSARY")
                .containsEntry("title", "纪念日1")
                .containsEntry("time", null);
        assertThat(items.get(0).get("members")).isEqualTo(List.of("妈妈"));
    }

    @Test
    void anniversaryLunarExpandsToGregorianDay() {
        // 农历八月十五 → 2026-09-25
        when(anniversaryService.list(FAMILY)).thenReturn(List.of(anniversary(2, "lunar", 8, 15, 0)));

        Map<String, Object> result = service.events(FAMILY,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), false);

        assertThat(day(result, "2026-09-25")).hasSize(1);
        assertThat(day(result, "2026-09-25").get(0)).containsEntry("calendar", "lunar");
    }

    @Test
    void guestSeesOnlyAnniversaries() {
        when(anniversaryService.list(FAMILY)).thenReturn(List.of(anniversary(1, "solar", 10, 30, 0)));

        Map<String, Object> result = service.events(FAMILY,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), true);

        assertThat(day(result, "2026-10-30")).hasSize(1);
        assertThat(undated(result)).isEmpty();
        // 游客口径不碰提醒/计划(这些需要登录)
        verifyNoInteractions(reminderMapper, planMapper, planTaskMapper);
    }

    @Test
    void nullFamilyYieldsEmptyCalendar() {
        Map<String, Object> result = service.events(null,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), false);

        assertThat(day(result, "2026-10-30")).isEmpty();
        assertThat(undated(result)).isEmpty();
    }

    // ---------------------------------------------------------------- 提醒与每日任务

    @Test
    void remindersExpandPerDayWithCheckedState() {
        when(anniversaryService.list(FAMILY)).thenReturn(List.of());
        when(planMapper.selectList(any())).thenReturn(List.of());
        Reminder once = reminder(1L, DictConst.REPEAT_ONCE, LocalDate.of(2026, 10, 30), LocalTime.of(9, 0), 0);
        Reminder onceDone = reminder(2L, DictConst.REPEAT_ONCE, LocalDate.of(2026, 10, 30), LocalTime.of(10, 0), 1);
        Reminder daily = reminder(3L, DictConst.REPEAT_DAILY, LocalDate.of(2026, 10, 1), LocalTime.of(7, 30), 0);
        Reminder planManaged = reminder(4L, DictConst.REPEAT_ONCE, LocalDate.of(2026, 10, 30), LocalTime.of(11, 0), 0);
        planManaged.setPlanId(9L);
        when(reminderMapper.selectList(any())).thenReturn(List.of(once, onceDone, daily, planManaged));
        ReminderCheck checked = new ReminderCheck();
        checked.setReminderId(3L);
        checked.setCheckDate(LocalDate.of(2026, 10, 30));
        when(checkMapper.selectList(any())).thenReturn(List.of(checked));

        Map<String, Object> result = service.events(FAMILY,
                LocalDate.of(2026, 10, 29), LocalDate.of(2026, 10, 30), false);

        List<Map<String, Object>> first = day(result, "2026-10-30");
        // 一次性未完成 + 每日任务;已完成的一次性与计划托管的提醒不出现
        assertThat(first).extracting(m -> m.get("kind"))
                .containsExactlyInAnyOrder("REMINDER", "TASK");
        Map<String, Object> task = first.stream()
                .filter(m -> "TASK".equals(m.get("kind"))).findFirst().orElseThrow();
        assertThat(task).containsEntry("repeat", DictConst.REPEAT_DAILY)
                .containsEntry("done", true)
                .containsEntry("doneToday", true);
        // 前一天每天任务未勾选
        Map<String, Object> previous = day(result, "2026-10-29").stream()
                .filter(m -> "TASK".equals(m.get("kind"))).findFirst().orElseThrow();
        assertThat(previous).containsEntry("done", false);
    }

    // ---------------------------------------------------------------- 计划与子任务

    @Test
    void plansAndSubtasksByDateWithUndatedList() {
        when(anniversaryService.list(FAMILY)).thenReturn(List.of());
        FamilyPlan dated = plan(1L, "换滤芯", LocalDate.of(2026, 10, 30), DictConst.PLAN_ACTIVE);
        FamilyPlan undated = plan(2L, "学做三道新菜", null, DictConst.PLAN_ACTIVE);
        // 取数顺序:先按目标日取计划(1),再取无日期计划(2);已取消的由 SQL 条件排除,不在此列
        when(planMapper.selectList(any())).thenReturn(List.of(dated, undated), List.of(undated));
        Reminder planReminder = reminder(11L, DictConst.REPEAT_ONCE, LocalDate.of(2026, 10, 30), LocalTime.of(9, 0), 0);
        planReminder.setPlanId(1L);
        when(reminderMapper.selectList(any())).thenReturn(List.of(planReminder));
        PlanTask task = new PlanTask();
        task.setId(21L);
        task.setPlanId(1L);
        task.setTitle("买滤芯");
        task.setDueDate(LocalDate.of(2026, 10, 31));
        task.setDone(0);
        when(planTaskMapper.selectList(any())).thenReturn(List.of(task));

        Map<String, Object> result = service.events(FAMILY,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), false);

        List<Map<String, Object>> day30 = day(result, "2026-10-30");
        assertThat(day30).hasSize(1);
        assertThat(day30.get(0)).containsEntry("kind", "PLAN")
                .containsEntry("title", "换滤芯")
                .containsEntry("time", "09:00")
                .containsEntry("status", DictConst.PLAN_ACTIVE);

        List<Map<String, Object>> day31 = day(result, "2026-10-31");
        assertThat(day31).hasSize(1);
        assertThat(day31.get(0)).containsEntry("kind", "PLAN_TASK")
                .containsEntry("planId", 1L)
                .containsEntry("done", false);

        assertThat(undated(result)).hasSize(1);
        assertThat(undated(result).get(0)).containsEntry("title", "学做三道新菜")
                .containsEntry("createdAt", "2026-10-12");
    }

    // ---------------------------------------------------------------- 近期提醒

    @Test
    void upcomingCollapsesRecurringAndKeepsOneOff() {
        when(anniversaryService.list(FAMILY)).thenReturn(List.of());
        when(planMapper.selectList(any())).thenReturn(List.of());
        LocalDate today = LocalDate.now();
        Reminder daily = reminder(3L, DictConst.REPEAT_DAILY, today.minusMonths(1), LocalTime.of(7, 30), 0);
        Reminder once = reminder(4L, DictConst.REPEAT_ONCE, today.plusDays(2), LocalTime.of(9, 0), 0);
        when(reminderMapper.selectList(any())).thenReturn(List.of(daily, once));
        when(checkMapper.selectList(any())).thenReturn(List.of());

        List<Map<String, Object>> items = service.upcoming(FAMILY, 7);

        assertThat(items).hasSize(2);
        assertThat(items.get(0)).containsEntry("kind", "TASK")
                .containsEntry("date", today.toString());
        assertThat(items.get(1)).containsEntry("kind", "REMINDER")
                .containsEntry("date", today.plusDays(2).toString());
    }

    @Test
    void upcomingSkipsDoneItems() {
        when(anniversaryService.list(FAMILY)).thenReturn(List.of());
        when(planMapper.selectList(any())).thenReturn(List.of());
        LocalDate today = LocalDate.now();
        Reminder done = reminder(4L, DictConst.REPEAT_ONCE, today, LocalTime.of(9, 0), 1);
        when(reminderMapper.selectList(any())).thenReturn(List.of(done));

        assertThat(service.upcoming(FAMILY, 7)).isEmpty();
    }

    @Test
    void upcomingForGuestIsEmpty() {
        assertThat(service.upcoming(null, 7)).isEmpty();
    }
}
