package com.ihomy.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ihomy.common.BizException;
import com.ihomy.common.DictConst;
import com.ihomy.dto.ReminderDTO;
import com.ihomy.entity.Notification;
import com.ihomy.entity.Reminder;
import com.ihomy.entity.ReminderCheck;
import com.ihomy.mapper.NotificationMapper;
import com.ihomy.mapper.ReminderCheckMapper;
import com.ihomy.mapper.ReminderMapper;
import com.ihomy.mapper.SysUserMapper;
import com.ihomy.service.access.ContentKind;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 提醒完成态与到点通知口径:一次性翻 done、循环按日期插删完成记录、
 * 删除连带清理、到点通知跳过已完成/已通知。纯逻辑断言(不起 Spring 上下文)。
 */
class ReminderServiceTest {

    private static final Long FAMILY = 2L;
    private static final Long USER = 7L;
    private static final Long REMINDER_ID = 31L;
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 30);

    private final ReminderMapper reminderMapper = mock(ReminderMapper.class);
    private final ReminderCheckMapper checkMapper = mock(ReminderCheckMapper.class);
    private final NotificationMapper notificationMapper = mock(NotificationMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final AudienceService audienceService = mock(AudienceService.class);
    private final ReminderService service = new ReminderService(
            reminderMapper, checkMapper, notificationMapper, userMapper, notificationService, audienceService);

    /** 单测没有 MyBatis-Plus 启动扫描,手动注册实体信息,否则 LambdaWrapper 的列名解析会报错 */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Reminder.class);
        TableInfoHelper.initTableInfo(assistant, ReminderCheck.class);
        TableInfoHelper.initTableInfo(assistant, Notification.class);
    }

    private Reminder reminder(String repeatType, LocalDate date, LocalTime time, int done) {
        Reminder r = new Reminder();
        r.setId(REMINDER_ID);
        r.setFamilyId(FAMILY);
        r.setTitle("吃药");
        r.setRemindDate(date);
        r.setRemindTime(time);
        r.setRepeatType(repeatType);
        r.setCalendar(DictConst.CALENDAR_SOLAR);
        r.setIsLeap(0);
        r.setDone(done);
        return r;
    }

    // ---------------------------------------------------------------- 按日完成

    @Test
    void toggleDoneOnceFlipsDoneColumn() {
        when(reminderMapper.selectById(REMINDER_ID)).thenReturn(
                reminder(DictConst.REPEAT_ONCE, TODAY, LocalTime.of(8, 0), 0));

        service.toggleDone(REMINDER_ID, FAMILY, USER, null);

        ArgumentCaptor<Reminder> captor = ArgumentCaptor.forClass(Reminder.class);
        verify(reminderMapper).updateById(captor.capture());
        assertThat(captor.getValue().getDone()).isEqualTo(1);
        verify(checkMapper, never()).insert(any(ReminderCheck.class));
    }

    @Test
    void toggleDoneOnceUnchecksWhenAlreadyDone() {
        when(reminderMapper.selectById(REMINDER_ID)).thenReturn(
                reminder(DictConst.REPEAT_ONCE, TODAY, LocalTime.of(8, 0), 1));

        service.toggleDone(REMINDER_ID, FAMILY, USER, null);

        ArgumentCaptor<Reminder> captor = ArgumentCaptor.forClass(Reminder.class);
        verify(reminderMapper).updateById(captor.capture());
        assertThat(captor.getValue().getDone()).isZero();
    }

    @Test
    void toggleDoneRecurringInsertsCheckRowForGivenDate() {
        when(reminderMapper.selectById(REMINDER_ID)).thenReturn(
                reminder(DictConst.REPEAT_DAILY, TODAY, LocalTime.of(8, 0), 0));
        when(checkMapper.selectOne(any())).thenReturn(null);

        LocalDate day = LocalDate.of(2026, 10, 29);
        service.toggleDone(REMINDER_ID, FAMILY, USER, day);

        ArgumentCaptor<ReminderCheck> captor = ArgumentCaptor.forClass(ReminderCheck.class);
        verify(checkMapper).insert(captor.capture());
        ReminderCheck saved = captor.getValue();
        assertThat(saved.getReminderId()).isEqualTo(REMINDER_ID);
        assertThat(saved.getFamilyId()).isEqualTo(FAMILY);
        assertThat(saved.getCheckDate()).isEqualTo(day);
        assertThat(saved.getDoneBy()).isEqualTo(USER);
        // 循环提醒不动 done 列,系列不终止
        verify(reminderMapper, never()).updateById(any(Reminder.class));
    }

    @Test
    void toggleDoneRecurringDeletesCheckRowWhenAlreadyChecked() {
        when(reminderMapper.selectById(REMINDER_ID)).thenReturn(
                reminder(DictConst.REPEAT_YEARLY, TODAY, LocalTime.of(8, 0), 0));
        ReminderCheck existing = new ReminderCheck();
        existing.setId(88L);
        existing.setReminderId(REMINDER_ID);
        existing.setCheckDate(TODAY);
        when(checkMapper.selectOne(any())).thenReturn(existing);

        service.toggleDone(REMINDER_ID, FAMILY, USER, TODAY);

        verify(checkMapper).deleteById(88L);
        verify(checkMapper, never()).insert(any(ReminderCheck.class));
    }

    @Test
    void deleteCascadesCheckRows() {
        when(reminderMapper.selectById(REMINDER_ID)).thenReturn(
                reminder(DictConst.REPEAT_DAILY, TODAY, LocalTime.of(8, 0), 0));

        service.delete(REMINDER_ID, FAMILY);

        verify(reminderMapper).deleteById(REMINDER_ID);
        verify(checkMapper).deleteByReminderId(REMINDER_ID);
        verify(audienceService).revokeContent(ContentKind.REMINDER, REMINDER_ID);
    }

    @Test
    void listMergesSharedRemindersAsReadOnly() {
        Reminder own = reminder(DictConst.REPEAT_ONCE, TODAY, LocalTime.of(8, 0), 0);
        Reminder shared = reminder(DictConst.REPEAT_ONCE, TODAY, LocalTime.of(9, 0), 0);
        shared.setId(77L);
        shared.setFamilyId(99L);
        shared.setTitle("爸妈体检");
        when(reminderMapper.selectList(any())).thenReturn(List.of(own));
        when(audienceService.sharedIds(ContentKind.REMINDER, FAMILY)).thenReturn(List.of(77L));
        when(reminderMapper.selectBatchIds(List.of(77L))).thenReturn(List.of(shared));
        when(audienceService.sharedSources(ContentKind.REMINDER, List.of(77L), FAMILY))
                .thenReturn(Map.of(77L, Map.of("familyName", "爸妈家", "alias", "我爸妈")));

        List<Map<String, Object>> list = service.list(FAMILY, true);

        assertThat(list).hasSize(2);
        assertThat(list.get(0)).containsEntry("title", "吃药").containsEntry("readOnly", false);
        Map<String, Object> sharedView = list.get(1);
        assertThat(sharedView).containsEntry("title", "爸妈体检").containsEntry("readOnly", true);
        assertThat((Map<String, Object>) sharedView.get("sharedFrom")).containsEntry("alias", "我爸妈");
    }

    @Test
    void listWithoutSharedSkipsAudienceLookup() {
        when(reminderMapper.selectList(any())).thenReturn(List.of(
                reminder(DictConst.REPEAT_ONCE, TODAY, LocalTime.of(8, 0), 0)));

        List<Map<String, Object>> list = service.list(FAMILY, false);

        assertThat(list).hasSize(1);
        verify(audienceService, never()).sharedIds(any(), any());
    }

    @Test
    void deleteRejectsPlanManagedReminder() {
        Reminder r = reminder(DictConst.REPEAT_ONCE, TODAY, LocalTime.of(8, 0), 0);
        r.setPlanId(5L);
        when(reminderMapper.selectById(REMINDER_ID)).thenReturn(r);

        assertThatThrownBy(() -> service.delete(REMINDER_ID, FAMILY))
                .isInstanceOf(BizException.class);
        verify(checkMapper, never()).deleteByReminderId(anyLong());
    }

    @Test
    void updateBackToOneOffClearsCheckRows() {
        when(reminderMapper.selectById(REMINDER_ID)).thenReturn(
                reminder(DictConst.REPEAT_DAILY, TODAY, LocalTime.of(8, 0), 0));
        ReminderDTO dto = new ReminderDTO();
        dto.setRepeatType(DictConst.REPEAT_ONCE);

        service.update(REMINDER_ID, FAMILY, dto);

        ArgumentCaptor<Reminder> captor = ArgumentCaptor.forClass(Reminder.class);
        verify(reminderMapper).updateById(captor.capture());
        assertThat(captor.getValue().getRepeatType()).isEqualTo(DictConst.REPEAT_ONCE);
        // 历史按日完成记录一并清理,避免切回循环时误判已完成
        verify(checkMapper).deleteByReminderId(REMINDER_ID);
    }

    @Test
    void updateToLunarYearlyRequiresLunarMonthAndDay() {
        when(reminderMapper.selectById(REMINDER_ID)).thenReturn(
                reminder(DictConst.REPEAT_ONCE, TODAY, LocalTime.of(8, 0), 0));
        ReminderDTO dto = new ReminderDTO();
        dto.setRepeatType(DictConst.REPEAT_YEARLY);
        dto.setCalendar(DictConst.CALENDAR_LUNAR);

        assertThatThrownBy(() -> service.update(REMINDER_ID, FAMILY, dto))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("农历");
        verify(reminderMapper, never()).updateById(any(Reminder.class));
    }

    // ---------------------------------------------------------------- 通知去重

    @Test
    void notifySkipsRecurringReminderCheckedToday() {
        when(reminderMapper.selectList(any())).thenReturn(List.of(
                reminder(DictConst.REPEAT_DAILY, TODAY, LocalTime.of(8, 0), 0)));
        ReminderCheck checked = new ReminderCheck();
        checked.setReminderId(REMINDER_ID);
        checked.setCheckDate(TODAY);
        when(checkMapper.selectList(any())).thenReturn(List.of(checked));

        service.notifyDue(TODAY, LocalTime.of(8, 1));

        verify(notificationService, never()).create(anyLong(), anyString(), anyString(),
                anyLong(), anyString(), any());
    }

    @Test
    void notifySkipsReminderAlreadyNotifiedToday() {
        when(reminderMapper.selectList(any())).thenReturn(List.of(
                reminder(DictConst.REPEAT_DAILY, TODAY, LocalTime.of(8, 0), 0)));
        when(checkMapper.selectList(any())).thenReturn(List.of());
        when(notificationMapper.selectCount(any())).thenReturn(1L);

        service.notifyDue(TODAY, LocalTime.of(8, 1));

        verify(notificationService, never()).create(anyLong(), anyString(), anyString(),
                anyLong(), anyString(), any());
    }

    @Test
    void notifySendsToEveryFamilyMemberWhenDue() {
        when(reminderMapper.selectList(any())).thenReturn(List.of(
                reminder(DictConst.REPEAT_DAILY, TODAY, LocalTime.of(8, 0), 0)));
        when(checkMapper.selectList(any())).thenReturn(List.of());
        when(notificationMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.selectMembersByFamily(FAMILY)).thenReturn(List.of(
                Map.of("id", USER), Map.of("id", 8L)));

        service.notifyDue(TODAY, LocalTime.of(8, 1));

        verify(notificationService).create(eq(USER), eq("reminder"), eq("提醒：吃药"),
                eq(REMINDER_ID), eq("reminder"), isNull());
        verify(notificationService).create(eq(8L), eq("reminder"), eq("提醒：吃药"),
                eq(REMINDER_ID), eq("reminder"), isNull());
    }

    @Test
    void notifySkipsReminderBeforeItsTime() {
        when(reminderMapper.selectList(any())).thenReturn(List.of(
                reminder(DictConst.REPEAT_DAILY, TODAY, LocalTime.of(20, 0), 0)));
        when(checkMapper.selectList(any())).thenReturn(List.of());

        service.notifyDue(TODAY, LocalTime.of(8, 1));

        verify(notificationService, never()).create(anyLong(), anyString(), anyString(),
                anyLong(), anyString(), any());
    }

    @Test
    void notifyYearlyLunarReminderUsesLunarCalendar() {
        // 2026-09-25 是农历八月十五
        Reminder lunar = reminder(DictConst.REPEAT_YEARLY, LocalDate.of(2024, 9, 17), LocalTime.of(8, 0), 0);
        lunar.setCalendar(DictConst.CALENDAR_LUNAR);
        lunar.setLunarMonth(8);
        lunar.setLunarDay(15);
        when(reminderMapper.selectList(any())).thenReturn(List.of(lunar));
        when(checkMapper.selectList(any())).thenReturn(List.of());
        when(notificationMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.selectMembersByFamily(FAMILY)).thenReturn(List.of(Map.of("id", USER)));

        service.notifyDue(LocalDate.of(2026, 9, 25), LocalTime.of(8, 1));

        verify(notificationService).create(eq(USER), eq("reminder"), eq("提醒：吃药"),
                eq(REMINDER_ID), eq("reminder"), isNull());
    }
}
