package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.DictConst;
import com.ihomy.common.RepeatSchedule;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.PlanDTO;
import com.ihomy.dto.PlanTaskDTO;
import com.ihomy.entity.FamilyPlan;
import com.ihomy.entity.PlanTask;
import com.ihomy.entity.Reminder;
import com.ihomy.entity.SysUser;
import com.ihomy.mapper.FamilyPlanMapper;
import com.ihomy.mapper.FamilyPlanTaskMapper;
import com.ihomy.mapper.ReminderMapper;
import com.ihomy.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 家庭计划业务:计划+子任务两级,勾选子任务后自动汇总计划完成状态与进度。
 * 计划可设置「提醒时间」,由服务层托管一条 family_reminder(plan_id 关联):计划→提醒单向同步,
 * 提醒勾完成不反向影响计划状态。
 */
@Service
@RequiredArgsConstructor
public class FamilyPlanService {

    private final FamilyPlanMapper planMapper;
    private final FamilyPlanTaskMapper taskMapper;
    private final ReminderMapper reminderMapper;
    private final SysUserMapper sysUserMapper;

    /** 家庭计划列表(含子任务、指派昵称、进度、提醒时间) */
    public List<Map<String, Object>> list(Long familyId) {
        List<FamilyPlan> plans = planMapper.selectList(new LambdaQueryWrapper<FamilyPlan>()
                .eq(FamilyPlan::getFamilyId, familyId)
                .orderByAsc(FamilyPlan::getStatus)
                .orderByDesc(FamilyPlan::getCreatedAt));
        List<Long> planIds = plans.stream().map(FamilyPlan::getId).collect(Collectors.toList());
        Map<Long, List<PlanTask>> tasksByPlan = tasksByPlan(planIds);
        Map<Long, LocalTime> remindTimes = planRemindTimes(planIds);
        Map<Long, String> nicknames = nicknameMap(
                tasksByPlan.values().stream().flatMap(List::stream).collect(Collectors.toList()));

        return plans.stream().map(p -> {
            List<PlanTask> tasks = tasksByPlan.getOrDefault(p.getId(), List.of());
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", p.getId());
            map.put("title", p.getTitle());
            map.put("description", p.getDescription());
            map.put("targetDate", p.getTargetDate());
            map.put("status", p.getStatus());
            map.put("createdBy", p.getCreatedBy());
            map.put("remindTime", remindTimes.get(p.getId()));
            map.put("assigneeName", joinNames(tasks, nicknames));
            map.put("doneCount", tasks.stream().filter(t -> t.getDone() == 1).count());
            map.put("totalCount", tasks.size());
            map.put("tasks", tasks.stream().map(t -> {
                Map<String, Object> tm = new java.util.HashMap<>();
                tm.put("id", t.getId());
                tm.put("title", t.getTitle());
                tm.put("assigneeId", t.getAssigneeId());
                tm.put("dueDate", t.getDueDate());
                tm.put("done", t.getDone());
                return tm;
            }).collect(Collectors.toList()));
            return map;
        }).collect(Collectors.toList());
    }

    /**
     * 今日视图:今天到期与已逾期的进行中计划 + 今天应触发的未完成提醒。
     * 计划按提醒时刻(空排后)、目标日、id 升序;提醒按时刻升序。
     */
    public Map<String, Object> today(Long familyId) {
        LocalDate today = LocalDate.now();
        List<FamilyPlan> plans = planMapper.selectList(new LambdaQueryWrapper<FamilyPlan>()
                .eq(FamilyPlan::getFamilyId, familyId)
                .eq(FamilyPlan::getStatus, DictConst.PLAN_ACTIVE)
                .le(FamilyPlan::getTargetDate, today));
        List<Long> planIds = plans.stream().map(FamilyPlan::getId).collect(Collectors.toList());
        Map<Long, List<PlanTask>> tasksByPlan = tasksByPlan(planIds);
        Map<Long, LocalTime> remindTimes = planRemindTimes(planIds);
        Map<Long, String> nicknames = nicknameMap(
                tasksByPlan.values().stream().flatMap(List::stream).collect(Collectors.toList()));

        List<Map<String, Object>> planRows = new ArrayList<>();
        for (FamilyPlan p : plans) {
            List<PlanTask> tasks = tasksByPlan.getOrDefault(p.getId(), List.of());
            long overdueDays = ChronoUnit.DAYS.between(p.getTargetDate(), today);
            Map<String, Object> m = new HashMap<>();
            m.put("id", p.getId());
            m.put("title", p.getTitle());
            m.put("targetDate", p.getTargetDate());
            m.put("remindTime", remindTimes.get(p.getId()));
            m.put("overdue", overdueDays > 0);
            m.put("overdueDays", overdueDays > 0 ? overdueDays : 0);
            m.put("doneCount", tasks.stream().filter(t -> t.getDone() == 1).count());
            m.put("totalCount", tasks.size());
            m.put("assigneeName", joinNames(tasks, nicknames));
            planRows.add(m);
        }
        planRows.sort(Comparator
                .comparing((Map<String, Object> m) -> (LocalTime) m.get("remindTime"),
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(m -> (LocalDate) m.get("targetDate"))
                .thenComparing(m -> (Long) m.get("id")));

        List<Map<String, Object>> reminderRows = reminderMapper.selectList(new LambdaQueryWrapper<Reminder>()
                        .eq(Reminder::getFamilyId, familyId)
                        .eq(Reminder::getDone, 0))
                .stream()
                .filter(r -> RepeatSchedule.dueToday(r.getRepeatType(), r.getRemindDate(), today))
                .sorted(Comparator.comparing(Reminder::getRemindTime).thenComparing(Reminder::getId))
                .map(r -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", r.getId());
                    m.put("title", r.getTitle());
                    m.put("content", r.getContent());
                    m.put("remindTime", r.getRemindTime());
                    m.put("repeatType", r.getRepeatType());
                    m.put("planId", r.getPlanId());
                    m.put("fromPlan", r.getPlanId() != null);
                    return m;
                }).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("plans", planRows);
        result.put("reminders", reminderRows);
        return result;
    }

    /** 创建计划:默认进行中;带提醒时间时同时建一条计划托管的提醒 */
    @Transactional
    public FamilyPlan create(Long userId, Long familyId, PlanDTO dto) {
        LocalTime remindTime = blankToNull(dto.getRemindTime()) == null ? null : parseRemindTime(dto.getRemindTime());
        if (remindTime != null && dto.getTargetDate() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "设置提醒时间需要先选择目标日期");
        }
        FamilyPlan plan = new FamilyPlan();
        plan.setFamilyId(familyId);
        plan.setTitle(dto.getTitle());
        plan.setDescription(dto.getDescription());
        plan.setTargetDate(dto.getTargetDate());
        plan.setStatus(DictConst.PLAN_ACTIVE);
        plan.setCreatedBy(userId);
        planMapper.insert(plan);
        if (remindTime != null) {
            insertPlanReminder(plan, familyId, remindTime);
        }
        return plan;
    }

    /** 编辑计划(标题/描述/目标日期/手动状态/提醒时间) */
    @Transactional
    public void update(Long id, Long familyId, PlanDTO dto) {
        FamilyPlan plan = require(id, familyId);
        boolean targetChanged = dto.getTargetDate() != null
                && !dto.getTargetDate().equals(plan.getTargetDate());
        if (dto.getTitle() != null) plan.setTitle(dto.getTitle());
        if (dto.getDescription() != null) plan.setDescription(dto.getDescription());
        if (dto.getTargetDate() != null) plan.setTargetDate(dto.getTargetDate());
        if (dto.getStatus() != null) plan.setStatus(DictConst.planStatus(dto.getStatus()));
        planMapper.updateById(plan);

        if (blankToNull(dto.getRemindTime()) == null) {
            if (dto.getRemindTime() != null) {
                // 显式传空串 = 清除计划提醒
                reminderMapper.delete(new LambdaQueryWrapper<Reminder>().eq(Reminder::getPlanId, id));
            } else if (targetChanged) {
                // 只改目标日期:关联提醒日期跟随,并重置完成态(未来还要响)
                Reminder linked = linkedReminder(id);
                if (linked != null) {
                    linked.setRemindDate(plan.getTargetDate());
                    linked.setDone(0);
                    reminderMapper.updateById(linked);
                }
            }
        } else {
            LocalTime time = parseRemindTime(dto.getRemindTime());
            if (plan.getTargetDate() == null) {
                throw new BizException(ResultCode.BAD_REQUEST, "设置提醒时间需要先选择目标日期");
            }
            Reminder linked = linkedReminder(id);
            if (linked == null) {
                insertPlanReminder(plan, familyId, time);
            } else {
                // 提醒内容跟随计划(标题/描述是派生值),日期/时刻同步,改日期则重置完成态
                linked.setTitle(plan.getTitle());
                linked.setContent(truncate(plan.getDescription(), 500));
                linked.setRemindDate(plan.getTargetDate());
                linked.setRemindTime(time);
                if (targetChanged) linked.setDone(0);
                reminderMapper.updateById(linked);
            }
        }

        if (DictConst.PLAN_DONE.equals(plan.getStatus())) {
            markPlanRemindersDone(id);
        }
    }

    @Transactional
    public void delete(Long id, Long familyId) {
        require(id, familyId);
        planMapper.deleteById(id);
        taskMapper.delete(new LambdaQueryWrapper<PlanTask>().eq(PlanTask::getPlanId, id));
        reminderMapper.delete(new LambdaQueryWrapper<Reminder>().eq(Reminder::getPlanId, id));
    }

    /** 添加子任务 */
    @Transactional
    public PlanTask addTask(Long planId, Long familyId, PlanTaskDTO dto) {
        require(planId, familyId);
        PlanTask t = new PlanTask();
        t.setPlanId(planId);
        t.setTitle(dto.getTitle());
        t.setAssigneeId(dto.getAssigneeId());
        t.setDueDate(dto.getDueDate());
        t.setDone(0);
        taskMapper.insert(t);
        syncPlanStatus(planId);
        return t;
    }

    /** 更新子任务(勾选完成/换指派/改截止),并同步计划状态 */
    @Transactional
    public void updateTask(Long taskId, Long familyId, PlanTaskDTO dto) {
        PlanTask t = taskMapper.selectById(taskId);
        if (t == null) throw new BizException(ResultCode.NOT_FOUND);
        require(t.getPlanId(), familyId); // 子任务归属计划须在本家庭
        if (dto.getTitle() != null) t.setTitle(dto.getTitle());
        if (dto.getAssigneeId() != null) t.setAssigneeId(dto.getAssigneeId());
        if (dto.getDueDate() != null) t.setDueDate(dto.getDueDate());
        if (dto.getDone() != null) t.setDone(dto.getDone());
        taskMapper.updateById(t);
        syncPlanStatus(t.getPlanId());
    }

    @Transactional
    public void deleteTask(Long taskId, Long familyId) {
        PlanTask t = taskMapper.selectById(taskId);
        if (t == null) throw new BizException(ResultCode.NOT_FOUND);
        require(t.getPlanId(), familyId);
        taskMapper.deleteById(taskId);
        syncPlanStatus(t.getPlanId());
    }

    /** 按子任务完成度同步计划:全部完成→完成(连带完成关联提醒),否则→进行中(手动取消不覆盖) */
    private void syncPlanStatus(Long planId) {
        FamilyPlan plan = planMapper.selectById(planId);
        if (plan == null || !DictConst.PLAN_ACTIVE.equals(plan.getStatus())) return;
        Long undone = taskMapper.selectCount(new LambdaQueryWrapper<PlanTask>()
                .eq(PlanTask::getPlanId, planId).eq(PlanTask::getDone, 0));
        plan.setStatus(undone != null && undone > 0 ? DictConst.PLAN_ACTIVE : DictConst.PLAN_DONE);
        planMapper.updateById(plan);
        if (DictConst.PLAN_DONE.equals(plan.getStatus())) {
            markPlanRemindersDone(planId);
        }
    }

    /** 计划侧建托管提醒:内容取计划标题/描述,日期=目标日,一次性 */
    private void insertPlanReminder(FamilyPlan plan, Long familyId, LocalTime time) {
        Reminder r = new Reminder();
        r.setFamilyId(familyId);
        r.setTitle(plan.getTitle());
        r.setContent(truncate(plan.getDescription(), 500));
        r.setRemindDate(plan.getTargetDate());
        r.setRemindTime(time);
        r.setRepeatType(DictConst.REPEAT_ONCE);
        r.setDone(0);
        r.setPlanId(plan.getId());
        r.setCreatedBy(plan.getCreatedBy());
        reminderMapper.insert(r);
    }

    private Reminder linkedReminder(Long planId) {
        List<Reminder> list = reminderMapper.selectList(new LambdaQueryWrapper<Reminder>()
                .eq(Reminder::getPlanId, planId).orderByAsc(Reminder::getId));
        return list.isEmpty() ? null : list.get(0);
    }

    /** 计划完成 → 关联提醒同步完成(单向:提醒勾完成不改计划) */
    private void markPlanRemindersDone(Long planId) {
        reminderMapper.update(null, new LambdaUpdateWrapper<Reminder>()
                .eq(Reminder::getPlanId, planId)
                .set(Reminder::getDone, 1));
    }

    /** 批量取计划关联的提醒时刻(一次查询,禁止逐条) */
    private Map<Long, LocalTime> planRemindTimes(List<Long> planIds) {
        if (planIds.isEmpty()) return Map.of();
        Map<Long, LocalTime> map = new HashMap<>();
        for (Reminder r : reminderMapper.selectList(new LambdaQueryWrapper<Reminder>()
                .in(Reminder::getPlanId, planIds).orderByAsc(Reminder::getId))) {
            map.putIfAbsent(r.getPlanId(), r.getRemindTime());
        }
        return map;
    }

    /** 批量取子任务并按计划分组(一次查询,禁止 N+1) */
    private Map<Long, List<PlanTask>> tasksByPlan(List<Long> planIds) {
        if (planIds.isEmpty()) return Map.of();
        return taskMapper.selectList(new LambdaQueryWrapper<PlanTask>()
                        .in(PlanTask::getPlanId, planIds)
                        .orderByAsc(PlanTask::getId))
                .stream()
                .collect(Collectors.groupingBy(PlanTask::getPlanId, LinkedHashMap::new, Collectors.toList()));
    }

    /** 批量取成员昵称(一次查询) */
    private Map<Long, String> nicknameMap(List<PlanTask> tasks) {
        List<Long> ids = tasks.stream().map(PlanTask::getAssigneeId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) return Map.of();
        return sysUserMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getNickname, (a, b) -> a));
    }

    private String joinNames(List<PlanTask> tasks, Map<Long, String> nicknames) {
        return tasks.stream().map(PlanTask::getAssigneeId)
                .filter(Objects::nonNull).distinct()
                .map(nicknames::get).filter(Objects::nonNull)
                .collect(Collectors.joining("、"));
    }

    private LocalTime parseRemindTime(String raw) {
        try {
            return LocalTime.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw new BizException(ResultCode.BAD_REQUEST, "提醒时间格式不正确，请使用 HH:mm");
        }
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v;
    }

    private static String truncate(String v, int max) {
        return v == null || v.length() <= max ? v : v.substring(0, max);
    }

    private FamilyPlan require(Long id, Long familyId) {
        FamilyPlan plan = planMapper.selectById(id);
        if (plan == null || !plan.getFamilyId().equals(familyId)) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        return plan;
    }
}
