package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.DictConst;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.ReminderDTO;
import com.ihomy.entity.Notification;
import com.ihomy.entity.Reminder;
import com.ihomy.mapper.NotificationMapper;
import com.ihomy.mapper.ReminderMapper;
import com.ihomy.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * 提醒事项业务:增删改查 + 定时触发(每分钟检查一次,到点给全家庭发站内通知)。
 * 重复规则:每日=每天提醒;每周=remind_date 的星期;每月=remind_date 的日号。
 * 同一天同一提醒只通知一次(以当日已存在该提醒的通知为准)。
 */
@Service
@RequiredArgsConstructor
public class ReminderService {

    private final ReminderMapper reminderMapper;
    private final NotificationMapper notificationMapper;
    private final SysUserMapper sysUserMapper;
    private final NotificationService notificationService;

    /** 家庭提醒列表:未完成优先,再按触发时间升序 */
    public List<Reminder> list(Long familyId) {
        return reminderMapper.selectList(new LambdaQueryWrapper<Reminder>()
                .eq(Reminder::getFamilyId, familyId)
                .orderByAsc(Reminder::getDone)
                .orderByAsc(Reminder::getRemindTime));
    }

    /** 新建提醒(BASE_HOUR 校验标题与时间) */
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
        r.setRepeatType(DictConst.repeatType(dto.getRepeatType()));
        r.setDone(0);
        r.setCreatedBy(userId);
        reminderMapper.insert(r);
        return r;
    }

    public void update(Long id, Long familyId, ReminderDTO dto) {
        Reminder r = require(id, familyId);
        if (dto.getTitle() != null) r.setTitle(dto.getTitle());
        if (dto.getContent() != null) r.setContent(dto.getContent());
        if (dto.getRemindDate() != null) r.setRemindDate(dto.getRemindDate());
        if (dto.getRemindTime() != null) r.setRemindTime(dto.getRemindTime());
        if (dto.getRepeatType() != null) r.setRepeatType(DictConst.repeatType(dto.getRepeatType()));
        reminderMapper.updateById(r);
    }

    public void delete(Long id, Long familyId) {
        reminderMapper.deleteById(require(id, familyId).getId());
    }

    /** 完成/取消完成勾选 */
    public void toggleDone(Long id, Long familyId) {
        Reminder r = require(id, familyId);
        r.setDone(r.getDone() == 1 ? 0 : 1);
        reminderMapper.updateById(r);
    }

    /** 每 5 分钟扫描一次:到点且当日未通知过的提醒,给全家成员发站内通知 */
    @Scheduled(fixedDelay = 300_000)
    public void processDue() {
        LocalTime now = LocalTime.now();
        if (now.isBefore(LocalTime.of(6, 0)) || now.isAfter(LocalTime.of(23, 0))) {
            return; // 夜间静默,避免打扰
        }
        List<Reminder> due = reminderMapper.selectList(new LambdaQueryWrapper<Reminder>()
                .eq(Reminder::getDone, 0));
        for (Reminder r : due) {
            // 今日是否应触发(一次性/每日/每周/每月)
            if (!dueToday(r)) continue;
            // 到点(预留 5 分钟触发窗口)
            if (r.getRemindTime().isAfter(now.plusMinutes(5))) continue;
            // 当日已通知则跳过(重复类提醒一天一次)
            if (notifiedToday(r)) continue;
            List<Map<String, Object>> members = sysUserMapper.selectMembersByFamily(r.getFamilyId());
            for (Map<String, Object> m : members) {
                notificationService.create((Long) m.get("id"), "reminder",
                        "提醒：" + r.getTitle(), r.getId(), "reminder", null);
            }
        }
    }

    /** 判断某提醒今天是否应触发:一次性看日期,重复类按星期/日号 */
    private boolean dueToday(Reminder r) {
        LocalDate today = LocalDate.now();
        return switch (r.getRepeatType() == null ? DictConst.REPEAT_ONCE : r.getRepeatType()) {
            case DictConst.REPEAT_DAILY -> true;
            case DictConst.REPEAT_WEEKLY -> r.getRemindDate().getDayOfWeek() == today.getDayOfWeek();
            case DictConst.REPEAT_MONTHLY -> r.getRemindDate().getDayOfMonth() == today.getDayOfMonth();
            default -> r.getRemindDate().equals(today);              // 一次性
        };
    }

    /** 当日是否已为某位成员发过该提醒通知(家庭为单位查重) */
    private boolean notifiedToday(Reminder r) {
        Long count = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getType, "reminder")
                .eq(Notification::getSourceId, r.getId())
                .ge(Notification::getCreatedAt, LocalDate.now().atStartOfDay()));
        return count != null && count > 0;
    }

    private Reminder require(Long id, Long familyId) {
        Reminder r = reminderMapper.selectById(id);
        if (r == null || !r.getFamilyId().equals(familyId)) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        return r;
    }
}