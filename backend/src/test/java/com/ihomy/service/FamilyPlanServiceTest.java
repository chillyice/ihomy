package com.ihomy.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ihomy.common.BizException;
import com.ihomy.common.DictConst;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.PlanDTO;
import com.ihomy.entity.FamilyPlan;
import com.ihomy.entity.PlanTask;
import com.ihomy.entity.Reminder;
import com.ihomy.entity.SysUser;
import com.ihomy.mapper.FamilyPlanMapper;
import com.ihomy.mapper.FamilyPlanTaskMapper;
import com.ihomy.mapper.ReminderMapper;
import com.ihomy.mapper.SysUserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 计划↔提醒托管规则:创建/编辑/清空/删除/完成态的联动,以及今日视图的取数口径。
 * 纯逻辑断言(不起 Spring 上下文)。
 */
class FamilyPlanServiceTest {

    private static final Long FAMILY = 2L;
    private static final Long USER = 7L;
    private static final Long PLAN_ID = 11L;

    private final FamilyPlanMapper planMapper = mock(FamilyPlanMapper.class);
    private final FamilyPlanTaskMapper taskMapper = mock(FamilyPlanTaskMapper.class);
    private final ReminderMapper reminderMapper = mock(ReminderMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final HomeStatsService homeStatsService = mock(HomeStatsService.class);
    private final FamilyPlanService service =
            new FamilyPlanService(planMapper, taskMapper, reminderMapper, userMapper, homeStatsService);

    /** 单测没有 MyBatis-Plus 启动扫描,手动注册实体信息,否则 LambdaWrapper 的列名解析会报错 */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, FamilyPlan.class);
        TableInfoHelper.initTableInfo(assistant, PlanTask.class);
        TableInfoHelper.initTableInfo(assistant, Reminder.class);
    }

    private PlanDTO dto(String title, LocalDate targetDate, String remindTime) {
        PlanDTO dto = new PlanDTO();
        dto.setTitle(title);
        dto.setDescription("清单");
        dto.setTargetDate(targetDate);
        dto.setRemindTime(remindTime);
        return dto;
    }

    private FamilyPlan plan(LocalDate targetDate, String status) {
        FamilyPlan p = new FamilyPlan();
        p.setId(PLAN_ID);
        p.setFamilyId(FAMILY);
        p.setTitle("换季大扫除");
        p.setDescription("客厅+卧室");
        p.setTargetDate(targetDate);
        p.setStatus(status);
        p.setCreatedBy(USER);
        return p;
    }

    private Reminder linked(LocalTime time, LocalDate date, int done) {
        Reminder r = new Reminder();
        r.setId(99L);
        r.setFamilyId(FAMILY);
        r.setPlanId(PLAN_ID);
        r.setTitle("换季大扫除");
        r.setRemindDate(date);
        r.setRemindTime(time);
        r.setRepeatType(DictConst.REPEAT_ONCE);
        r.setDone(done);
        return r;
    }

    private void planInsertAssignsId() {
        when(planMapper.insert(any(FamilyPlan.class))).thenAnswer(inv -> {
            inv.<FamilyPlan>getArgument(0).setId(PLAN_ID);
            return 1;
        });
    }

    // ---------------------------------------------------------------- 创建

    @Test
    void createWithTimeWithoutTargetDateRejected() {
        assertThatThrownBy(() -> service.create(USER, FAMILY, dto("大扫除", null, "09:30")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("目标日期")
                .satisfies(e -> assertThat(((BizException) e).getCode())
                        .isEqualTo(ResultCode.BAD_REQUEST.getCode()));
        verify(reminderMapper, never()).insert(any(Reminder.class));
    }

    @Test
    void createWithTimeInsertsLinkedReminder() {
        planInsertAssignsId();
        LocalDate target = LocalDate.now().plusDays(2);
        service.create(USER, FAMILY, dto("大扫除", target, "09:30"));

        ArgumentCaptor<Reminder> captor = ArgumentCaptor.forClass(Reminder.class);
        verify(reminderMapper).insert(captor.capture());
        Reminder r = captor.getValue();
        assertThat(r.getPlanId()).isEqualTo(PLAN_ID);
        assertThat(r.getFamilyId()).isEqualTo(FAMILY);
        assertThat(r.getTitle()).isEqualTo("大扫除");
        assertThat(r.getRemindDate()).isEqualTo(target);
        assertThat(r.getRemindTime()).isEqualTo(LocalTime.of(9, 30));
        assertThat(r.getRepeatType()).isEqualTo(DictConst.REPEAT_ONCE);
        assertThat(r.getDone()).isZero();
    }

    @Test
    void createWithoutTimeInsertsNoReminder() {
        planInsertAssignsId();
        service.create(USER, FAMILY, dto("大扫除", LocalDate.now(), null));
        verify(reminderMapper, never()).insert(any(Reminder.class));
    }

    @Test
    void createWithBlankTimeInsertsNoReminder() {
        planInsertAssignsId();
        service.create(USER, FAMILY, dto("大扫除", LocalDate.now(), "  "));
        verify(reminderMapper, never()).insert(any(Reminder.class));
    }

    @Test
    void createWithBadTimeRejected() {
        planInsertAssignsId();
        assertThatThrownBy(() -> service.create(USER, FAMILY, dto("大扫除", LocalDate.now(), "9点半")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("HH:mm");
    }

    // ---------------------------------------------------------------- 编辑

    @Test
    void updateTimeCreatesReminderWhenMissing() {
        FamilyPlan stored = plan(LocalDate.now().plusDays(1), DictConst.PLAN_ACTIVE);
        when(planMapper.selectById(PLAN_ID)).thenReturn(stored);
        when(reminderMapper.selectList(any())).thenReturn(List.of());

        service.update(PLAN_ID, FAMILY, dto(null, null, "18:00"));

        ArgumentCaptor<Reminder> captor = ArgumentCaptor.forClass(Reminder.class);
        verify(reminderMapper).insert(captor.capture());
        assertThat(captor.getValue().getPlanId()).isEqualTo(PLAN_ID);
        assertThat(captor.getValue().getRemindTime()).isEqualTo(LocalTime.of(18, 0));
    }

    @Test
    void updateTimeMovesLinkedReminderAndResetsDoneWhenDateChanges() {
        FamilyPlan stored = plan(LocalDate.of(2026, 10, 1), DictConst.PLAN_ACTIVE);
        Reminder existing = linked(LocalTime.of(8, 0), LocalDate.of(2026, 10, 1), 1);
        when(planMapper.selectById(PLAN_ID)).thenReturn(stored);
        when(reminderMapper.selectList(any())).thenReturn(List.of(existing));

        service.update(PLAN_ID, FAMILY, dto(null, LocalDate.of(2026, 11, 1), "20:15"));

        ArgumentCaptor<Reminder> captor = ArgumentCaptor.forClass(Reminder.class);
        verify(reminderMapper).updateById(captor.capture());
        Reminder saved = captor.getValue();
        assertThat(saved.getRemindDate()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(saved.getRemindTime()).isEqualTo(LocalTime.of(20, 15));
        assertThat(saved.getDone()).isZero();
        verify(reminderMapper, never()).insert(any(Reminder.class));
    }

    @Test
    void updateTargetDateOnlyMovesReminderAndResetsDone() {
        FamilyPlan stored = plan(LocalDate.of(2026, 10, 1), DictConst.PLAN_ACTIVE);
        Reminder existing = linked(LocalTime.of(8, 0), LocalDate.of(2026, 10, 1), 1);
        when(planMapper.selectById(PLAN_ID)).thenReturn(stored);
        when(reminderMapper.selectList(any())).thenReturn(List.of(existing));

        service.update(PLAN_ID, FAMILY, dto(null, LocalDate.of(2026, 12, 24), null));

        ArgumentCaptor<Reminder> captor = ArgumentCaptor.forClass(Reminder.class);
        verify(reminderMapper).updateById(captor.capture());
        assertThat(captor.getValue().getRemindDate()).isEqualTo(LocalDate.of(2026, 12, 24));
        assertThat(captor.getValue().getDone()).isZero();
    }

    @Test
    void updateBlankTimeClearsLinkedReminder() {
        FamilyPlan stored = plan(LocalDate.now(), DictConst.PLAN_ACTIVE);
        when(planMapper.selectById(PLAN_ID)).thenReturn(stored);

        service.update(PLAN_ID, FAMILY, dto(null, null, ""));

        verify(reminderMapper).delete(any());
        verify(reminderMapper, never()).insert(any(Reminder.class));
    }

    @Test
    void updateStatusDoneMarksLinkedReminderDone() {
        FamilyPlan stored = plan(LocalDate.now(), DictConst.PLAN_ACTIVE);
        when(planMapper.selectById(PLAN_ID)).thenReturn(stored);
        PlanDTO dto = dto(null, null, null);
        dto.setStatus(DictConst.PLAN_DONE);

        service.update(PLAN_ID, FAMILY, dto);

        assertThat(stored.getStatus()).isEqualTo(DictConst.PLAN_DONE);
        verify(reminderMapper).update(isNull(), any());
    }

    // ---------------------------------------------------------------- 删除/完成

    @Test
    void deleteAlsoRemovesLinkedReminder() {
        when(planMapper.selectById(PLAN_ID)).thenReturn(plan(LocalDate.now(), DictConst.PLAN_ACTIVE));

        service.delete(PLAN_ID, FAMILY);

        verify(reminderMapper).delete(any());
    }

    @Test
    void completingLastTaskMarksPlanAndReminderDone() {
        FamilyPlan stored = plan(LocalDate.now(), DictConst.PLAN_ACTIVE);
        when(planMapper.selectById(PLAN_ID)).thenReturn(stored);
        when(taskMapper.selectCount(any())).thenReturn(0L);
        PlanTask task = new PlanTask();
        task.setId(5L);
        task.setPlanId(PLAN_ID);
        task.setDone(0);
        when(taskMapper.selectById(5L)).thenReturn(task);

        com.ihomy.dto.PlanTaskDTO dto = new com.ihomy.dto.PlanTaskDTO();
        dto.setDone(1);
        service.updateTask(5L, FAMILY, dto);

        assertThat(stored.getStatus()).isEqualTo(DictConst.PLAN_DONE);
        verify(reminderMapper).update(isNull(), any());
    }

    @Test
    void completingTaskKeepsPlanActiveWhileOthersPending() {
        FamilyPlan stored = plan(LocalDate.now(), DictConst.PLAN_ACTIVE);
        when(planMapper.selectById(PLAN_ID)).thenReturn(stored);
        when(taskMapper.selectCount(any())).thenReturn(2L);
        PlanTask task = new PlanTask();
        task.setId(5L);
        task.setPlanId(PLAN_ID);
        task.setDone(0);
        when(taskMapper.selectById(5L)).thenReturn(task);

        com.ihomy.dto.PlanTaskDTO dto = new com.ihomy.dto.PlanTaskDTO();
        dto.setDone(1);
        service.updateTask(5L, FAMILY, dto);

        assertThat(stored.getStatus()).isEqualTo(DictConst.PLAN_ACTIVE);
        verify(reminderMapper, never()).update(any(), any());
    }

    // ---------------------------------------------------------------- 今日视图

    @Test
    void todaySplitsPlansAndRemindersWithOverdueAndOrder() {
        LocalDate today = LocalDate.now();
        FamilyPlan todayPlan = plan(today, DictConst.PLAN_ACTIVE);
        todayPlan.setId(1L);
        FamilyPlan overduePlan = plan(today.minusDays(3), DictConst.PLAN_ACTIVE);
        overduePlan.setId(2L);
        when(planMapper.selectList(any())).thenReturn(List.of(overduePlan, todayPlan));

        PlanTask done = new PlanTask();
        done.setId(21L);
        done.setPlanId(1L);
        done.setDone(1);
        PlanTask pending = new PlanTask();
        pending.setId(22L);
        pending.setPlanId(1L);
        pending.setDone(0);
        pending.setAssigneeId(USER);
        when(taskMapper.selectList(any())).thenReturn(List.of(done, pending));

        Reminder planReminder = linked(LocalTime.of(9, 30), today, 0);
        planReminder.setId(30L);
        planReminder.setPlanId(1L);
        planReminder.setTitle("今日计划提醒");
        Reminder daily = new Reminder();
        daily.setId(31L);
        daily.setFamilyId(FAMILY);
        daily.setTitle("吃药");
        daily.setRemindDate(today.minusDays(10));
        daily.setRemindTime(LocalTime.of(8, 0));
        daily.setRepeatType(DictConst.REPEAT_DAILY);
        daily.setDone(0);
        Reminder future = new Reminder();
        future.setId(32L);
        future.setFamilyId(FAMILY);
        future.setTitle("下周的事");
        future.setRemindDate(today.plusDays(7));
        future.setRemindTime(LocalTime.of(7, 0));
        future.setRepeatType(DictConst.REPEAT_ONCE);
        future.setDone(0);
        when(reminderMapper.selectList(any())).thenReturn(List.of(planReminder, daily, future));

        SysUser member = new SysUser();
        member.setId(USER);
        member.setNickname("妈妈");
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(member));
        when(homeStatsService.upcomingEvents(FAMILY, 3)).thenReturn(List.of());

        Map<String, Object> result = service.today(FAMILY);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> plans = (List<Map<String, Object>>) result.get("plans");
        assertThat(plans).hasSize(2);
        // 有提醒时刻的计划排前(09:30),逾期的无时刻计划排后
        assertThat(plans.get(0).get("id")).isEqualTo(1L);
        assertThat(plans.get(0).get("remindTime")).isEqualTo(LocalTime.of(9, 30));
        assertThat(plans.get(0).get("overdue")).isEqualTo(false);
        assertThat(plans.get(0).get("doneCount")).isEqualTo(1L);
        assertThat(plans.get(0).get("totalCount")).isEqualTo(2);
        assertThat(plans.get(0).get("assigneeName")).isEqualTo("妈妈");
        assertThat(plans.get(1).get("overdue")).isEqualTo(true);
        assertThat(plans.get(1).get("overdueDays")).isEqualTo(3L);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> reminders = (List<Map<String, Object>>) result.get("reminders");
        // 未来的一次性提醒不出现;按时刻升序
        assertThat(reminders).hasSize(2);
        assertThat(reminders.get(0).get("id")).isEqualTo(31L);
        assertThat(reminders.get(1).get("id")).isEqualTo(30L);
        assertThat(reminders.get(1).get("fromPlan")).isEqualTo(true);
        assertThat(reminders.get(0).get("fromPlan")).isEqualTo(false);
        // 重要日期只做提示,与首页倒计时同源(本用例没有纪念日)
        assertThat((List<?>) result.get("events")).isEmpty();
    }

    @Test
    void listAttachesRemindTimeInOneBatch() {
        FamilyPlan p = plan(LocalDate.now(), DictConst.PLAN_ACTIVE);
        p.setId(1L);
        when(planMapper.selectList(any())).thenReturn(List.of(p));
        when(taskMapper.selectList(any())).thenReturn(List.of());
        Reminder planReminder = linked(LocalTime.of(7, 45), LocalDate.now(), 0);
        planReminder.setPlanId(1L);
        when(reminderMapper.selectList(any())).thenReturn(List.of(planReminder));

        List<Map<String, Object>> rows = service.list(FAMILY);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("remindTime")).isEqualTo(LocalTime.of(7, 45));
        // 计划/子任务/提醒各一次查询,不随计划数增长
        verify(reminderMapper, times(1)).selectList(any());
        verify(taskMapper, times(1)).selectList(any());
    }
}
