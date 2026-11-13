package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.DictConst;
import com.ihomy.common.RepeatSchedule;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.ReminderDTO;
import com.ihomy.entity.Notification;
import com.ihomy.entity.Reminder;
import com.ihomy.entity.ReminderCheck;
import com.ihomy.mapper.NotificationMapper;
import com.ihomy.mapper.ReminderCheckMapper;
import com.ihomy.mapper.ReminderMapper;
import com.ihomy.mapper.SysUserMapper;
import com.ihomy.service.access.ContentKind;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 提醒事项业务:增删改查 + 定时触发(每 5 分钟检查一次,到点给全家庭发站内通知)。
 * 重复规则:每日=每天;每周=remind_date 的星期;每月=remind_date 的日号;
 * 每年=阳历月日或农历月日(闰月严格匹配),判定唯一来源是 {@link RepeatSchedule}。
 * 完成态:一次性用 done 列;循环提醒按 (提醒, 日期) 记在 family_reminder_check,系列不终止。
 * 同一天同一提醒只通知一次(以当日已存在该提醒的通知为准)。
 * 关联计划的提醒(plan_id 非空)由家庭计划托管,提醒侧只允许勾完成。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderService {

    /** 计划托管的提醒在提醒侧不允许改内容/时间/删除 */
    private static final String PLAN_MANAGED = "该提醒由家庭计划管理，请到家庭计划中修改";

    private final ReminderMapper reminderMapper;
    private final ReminderCheckMapper reminderCheckMapper;
    private final NotificationMapper notificationMapper;
    private final SysUserMapper sysUserMapper;
    private final NotificationService notificationService;
    private final AudienceService audienceService;

    /**
     * 家庭提醒列表:未完成优先,再按触发时间升序。
     * includeShared=true 时并入亲友圈分享给本家庭的提醒(只读,带来源家庭)。
     */
    public List<Map<String, Object>> list(Long familyId, boolean includeShared) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (familyId == null) return result;
        List<Reminder> all = new ArrayList<>(reminderMapper.selectList(new LambdaQueryWrapper<Reminder>()
                .eq(Reminder::getFamilyId, familyId)));
        Map<Long, Map<String, Object>> sharedSources = Map.of();
        if (includeShared) {
            List<Long> sharedIds = audienceService.sharedIds(ContentKind.REMINDER, familyId);
            if (!sharedIds.isEmpty()) {
                sharedSources = audienceService.sharedSources(ContentKind.REMINDER, sharedIds, familyId);
                all.addAll(reminderMapper.selectBatchIds(sharedIds));
            }
        }
        // 合并两批数据后在内存里排一次(未完成在前,再按提醒时刻,空时刻在前)
        all.sort(Comparator
                .comparing((Reminder r) -> r.getDone() == null ? 0 : r.getDone())
                .thenComparing(Reminder::getRemindTime, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(Reminder::getId));
        for (Reminder r : all) {
            result.add(toView(r, familyId, sharedSources.get(r.getId())));
        }
        return result;
    }

    /** 提醒 → 展示结构;跨家庭条目带 readOnly 与来源家庭 */
    private Map<String, Object> toView(Reminder r, Long viewerFamilyId, Map<String, Object> sharedFrom) {
        boolean readOnly = !viewerFamilyId.equals(r.getFamilyId());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("title", r.getTitle());
        m.put("content", r.getContent());
        m.put("remindDate", r.getRemindDate());
        m.put("remindTime", r.getRemindTime());
        m.put("repeatType", r.getRepeatType());
        m.put("calendar", r.getCalendar());
        m.put("lunarMonth", r.getLunarMonth());
        m.put("lunarDay", r.getLunarDay());
        m.put("isLeap", r.getIsLeap());
        m.put("done", r.getDone());
        m.put("planId", r.getPlanId());
        m.put("createdBy", r.getCreatedBy());
        m.put("readOnly", readOnly);
        if (readOnly) m.put("sharedFrom", sharedFrom);
        return m;
    }

    /** 新建提醒(校验标题、日期与时刻;每年重复校验历法与农历字段) */
    public Reminder create(Long userId, Long familyId, ReminderDTO dto) {
        if (dto.getTitle() == null || dto.getTitle().isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "请填写提醒标题");
        }
        if (dto.getRemindDate() == null || dto.getRemindTime() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "请填写提醒日期与时间");
        }
        Reminder r = new Reminder();
        r.setFamilyId(familyId);
        r.setTitle(dto.getTitle());
        r.setContent(dto.getContent());
        r.setRemindDate(dto.getRemindDate());
        r.setRemindTime(dto.getRemindTime());
        applyRepeat(r, dto);
        r.setDone(0);
        r.setCreatedBy(userId);
        reminderMapper.insert(r);
        return r;
    }

    public void update(Long id, Long familyId, ReminderDTO dto) {
        Reminder r = require(id, familyId);
        if (r.getPlanId() != null) throw new BizException(ResultCode.BAD_REQUEST, PLAN_MANAGED);
        if (dto.getTitle() != null) r.setTitle(dto.getTitle());
        if (dto.getContent() != null) r.setContent(dto.getContent());
        if (dto.getRemindDate() != null) r.setRemindDate(dto.getRemindDate());
        if (dto.getRemindTime() != null) r.setRemindTime(dto.getRemindTime());
        // 重复周期是组合字段(周期+历法+农历月日),只有前端明确给了周期才整块重算
        if (dto.getRepeatType() != null) {
            applyRepeat(r, dto);
            // 循环改回一次性:历史按日完成记录不再有意义,避免切回循环时误判为已完成
            if (!isRecurring(r)) reminderCheckMapper.deleteByReminderId(id);
        }
        reminderMapper.updateById(r);
    }

    /** 删除提醒:连带清理按日完成记录与分享范围(表无逻辑删列,走自定义 XML 物理删) */
    public void delete(Long id, Long familyId) {
        Reminder r = require(id, familyId);
        if (r.getPlanId() != null) throw new BizException(ResultCode.BAD_REQUEST, PLAN_MANAGED);
        reminderMapper.deleteById(id);
        reminderCheckMapper.deleteByReminderId(id);
        audienceService.revokeContent(ContentKind.REMINDER, id);
    }

    /**
     * 完成/取消完成勾选。
     * 一次性提醒翻 done 列;循环提醒按日期插入/删除当日完成记录(缺省今天),系列本身不终止。
     *
     * @param date 完成日期(空=今天;一次性的日期参数忽略)
     */
    public void toggleDone(Long id, Long familyId, Long userId, LocalDate date) {
        Reminder r = require(id, familyId);
        if (!isRecurring(r)) {
            r.setDone(r.getDone() == 1 ? 0 : 1);
            reminderMapper.updateById(r);
            return;
        }
        LocalDate day = date == null ? LocalDate.now() : date;
        ReminderCheck existing = reminderCheckMapper.selectOne(new LambdaQueryWrapper<ReminderCheck>()
                .eq(ReminderCheck::getReminderId, id)
                .eq(ReminderCheck::getCheckDate, day));
        if (existing != null) {
            reminderCheckMapper.deleteById(existing.getId());
            return;
        }
        ReminderCheck check = new ReminderCheck();
        check.setReminderId(id);
        check.setFamilyId(familyId);
        check.setCheckDate(day);
        check.setDoneBy(userId);
        reminderCheckMapper.insert(check);
    }

    /** 每 5 分钟扫描一次:到点且当日未通知过的提醒,给全家成员发站内通知 */
    @Scheduled(fixedDelay = 300_000)
    public void processDue() {
        LocalTime now = LocalTime.now();
        if (now.isBefore(LocalTime.of(6, 0)) || now.isAfter(LocalTime.of(23, 0))) {
            return; // 夜间静默,避免打扰
        }
        notifyDue(LocalDate.now(), now);
    }

    /** 到点通知主流程(日期/时刻由调用方给,便于单测固定时间) */
    void notifyDue(LocalDate today, LocalTime now) {
        List<Reminder> candidates = reminderMapper.selectList(new LambdaQueryWrapper<Reminder>()
                .eq(Reminder::getDone, 0));
        Set<Long> checkedReminderIds = checkedReminderIds(candidates, today);
        for (Reminder r : candidates) {
            try {
                if (checkedReminderIds.contains(r.getId())) continue;
                // 该提醒在今天是否应触发(一次性/每日/每周/每月/每年)
                if (!RepeatSchedule.dueToday(r.getRepeatType(), r.getRemindDate(), today,
                        r.getCalendar(), r.getLunarMonth(), r.getLunarDay(), r.getIsLeap())) continue;
                // 到点(预留 5 分钟触发窗口)
                if (r.getRemindTime() == null || r.getRemindTime().isAfter(now.plusMinutes(5))) continue;
                // 当日已通知则跳过(重复类提醒一天一次,防止 5 分钟重复推送)
                if (notifiedToday(r, today)) continue;
                notifyFamily(r);
            } catch (Exception e) {
                // 单条提醒处理失败不影响其他提醒
                log.error("reminder notify failed, reminderId={}", r.getId(), e);
            }
        }
    }

    /** 当日已完成的循环提醒 id 集合(一次查询,禁止逐条查) */
    private Set<Long> checkedReminderIds(List<Reminder> candidates, LocalDate today) {
        List<Long> ids = new ArrayList<>();
        for (Reminder r : candidates) {
            if (isRecurring(r)) ids.add(r.getId());
        }
        if (ids.isEmpty()) return Set.of();
        Set<Long> result = new HashSet<>();
        for (ReminderCheck c : reminderCheckMapper.selectList(new LambdaQueryWrapper<ReminderCheck>()
                .in(ReminderCheck::getReminderId, ids)
                .eq(ReminderCheck::getCheckDate, today))) {
            result.add(c.getReminderId());
        }
        return result;
    }

    /** 给全家庭成员发一条站内通知 */
    private void notifyFamily(Reminder r) {
        List<Map<String, Object>> members = sysUserMapper.selectMembersByFamily(r.getFamilyId());
        for (Map<String, Object> m : members) {
            notificationService.create((Long) m.get("id"), "reminder",
                    "提醒：" + r.getTitle(), r.getId(), "reminder", null);
        }
    }

    /** 当日是否已为某位成员发过该提醒通知(家庭为单位查重) */
    private boolean notifiedToday(Reminder r, LocalDate today) {
        Long count = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getType, "reminder")
                .eq(Notification::getSourceId, r.getId())
                .ge(Notification::getCreatedAt, today.atStartOfDay()));
        return count != null && count > 0;
    }

    /** 循环提醒:ONCE 以外都按日记录完成 */
    public static boolean isRecurring(Reminder r) {
        return r.getRepeatType() != null && !DictConst.REPEAT_ONCE.equals(r.getRepeatType());
    }

    /** 重复周期整块落库:非每年重复一律按阳历且清空农历字段 */
    private void applyRepeat(Reminder r, ReminderDTO dto) {
        String type = DictConst.repeatType(dto.getRepeatType());
        r.setRepeatType(type);
        if (!DictConst.REPEAT_YEARLY.equals(type)) {
            r.setCalendar(DictConst.CALENDAR_SOLAR);
            r.setLunarMonth(null);
            r.setLunarDay(null);
            r.setIsLeap(0);
            return;
        }
        String calendar = DictConst.calendar(dto.getCalendar());
        r.setCalendar(calendar);
        if (!DictConst.CALENDAR_LUNAR.equals(calendar)) {
            r.setLunarMonth(null);
            r.setLunarDay(null);
            r.setIsLeap(0);
            return;
        }
        Integer month = dto.getLunarMonth();
        Integer day = dto.getLunarDay();
        if (month == null || day == null || month < 1 || month > 12 || day < 1 || day > 30) {
            throw new BizException(ResultCode.BAD_REQUEST, "请选择农历月份与日期");
        }
        r.setLunarMonth(month);
        r.setLunarDay(day);
        r.setIsLeap(dto.getIsLeap() != null && dto.getIsLeap() == 1 ? 1 : 0);
    }

    private Reminder require(Long id, Long familyId) {
        Reminder r = reminderMapper.selectById(id);
        if (r == null || !r.getFamilyId().equals(familyId)) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        return r;
    }
}
