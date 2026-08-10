package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.DictConst;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.PlanDTO;
import com.ihomy.dto.PlanTaskDTO;
import com.ihomy.entity.FamilyPlan;
import com.ihomy.entity.PlanTask;
import com.ihomy.entity.SysUser;
import com.ihomy.mapper.FamilyPlanMapper;
import com.ihomy.mapper.FamilyPlanTaskMapper;
import com.ihomy.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 家庭计划业务:计划+子任务两级,勾选子任务后自动汇总计划完成状态与进度。
 */
@Service
@RequiredArgsConstructor
public class FamilyPlanService {

    private final FamilyPlanMapper planMapper;
    private final FamilyPlanTaskMapper taskMapper;
    private final SysUserMapper sysUserMapper;

    /** 家庭计划列表(含子任务、指派昵称、进度) */
    public List<Map<String, Object>> list(Long familyId) {
        List<FamilyPlan> plans = planMapper.selectList(new LambdaQueryWrapper<FamilyPlan>()
                .eq(FamilyPlan::getFamilyId, familyId)
                .orderByAsc(FamilyPlan::getStatus)
                .orderByDesc(FamilyPlan::getCreatedAt));
        return plans.stream().map(p -> {
            List<PlanTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<PlanTask>()
                    .eq(PlanTask::getPlanId, p.getId()));
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", p.getId());
            map.put("title", p.getTitle());
            map.put("description", p.getDescription());
            map.put("targetDate", p.getTargetDate());
            map.put("status", p.getStatus());
            map.put("createdBy", p.getCreatedBy());
            map.put("assigneeName", resolveNames(tasks));
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

    /** 创建计划:默认进行中 */
    public FamilyPlan create(Long userId, Long familyId, PlanDTO dto) {
        FamilyPlan plan = new FamilyPlan();
        plan.setFamilyId(familyId);
        plan.setTitle(dto.getTitle());
        plan.setDescription(dto.getDescription());
        plan.setTargetDate(dto.getTargetDate());
        plan.setStatus(DictConst.PLAN_ACTIVE);
        plan.setCreatedBy(userId);
        planMapper.insert(plan);
        return plan;
    }

    /** 编辑计划(标题/描述/目标日期/手动状态) */
    public void update(Long id, Long familyId, PlanDTO dto) {
        FamilyPlan plan = require(id, familyId);
        if (dto.getTitle() != null) plan.setTitle(dto.getTitle());
        if (dto.getDescription() != null) plan.setDescription(dto.getDescription());
        if (dto.getTargetDate() != null) plan.setTargetDate(dto.getTargetDate());
        if (dto.getStatus() != null) plan.setStatus(DictConst.planStatus(dto.getStatus()));
        planMapper.updateById(plan);
    }

    public void delete(Long id, Long familyId) {
        require(id, familyId);
        planMapper.deleteById(id);
        taskMapper.delete(new LambdaQueryWrapper<PlanTask>().eq(PlanTask::getPlanId, id));
    }

    /** 添加子任务 */
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

    public void deleteTask(Long taskId, Long familyId) {
        PlanTask t = taskMapper.selectById(taskId);
        if (t == null) throw new BizException(ResultCode.NOT_FOUND);
        require(t.getPlanId(), familyId);
        taskMapper.deleteById(taskId);
        syncPlanStatus(t.getPlanId());
    }

    /** 按子任务完成度同步计划:全部完成→完成,否则→进行中(手动取消不覆盖) */
    private void syncPlanStatus(Long planId) {
        FamilyPlan plan = planMapper.selectById(planId);
        if (plan == null || !DictConst.PLAN_ACTIVE.equals(plan.getStatus())) return;
        Long undone = taskMapper.selectCount(new LambdaQueryWrapper<PlanTask>()
                .eq(PlanTask::getPlanId, planId).eq(PlanTask::getDone, 0));
        plan.setStatus(undone != null && undone > 0 ? DictConst.PLAN_ACTIVE : DictConst.PLAN_DONE);
        planMapper.updateById(plan);
    }

    /** 子任务涉及的成员昵称快照(逗号连接,给列表展示) */
    private String resolveNames(List<PlanTask> tasks) {
        List<Long> ids = tasks.stream().map(PlanTask::getAssigneeId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) return "";
        return sysUserMapper.selectBatchIds(ids).stream()
                .map(SysUser::getNickname).collect(Collectors.joining("、"));
    }

    private FamilyPlan require(Long id, Long familyId) {
        FamilyPlan plan = planMapper.selectById(id);
        if (plan == null || !plan.getFamilyId().equals(familyId)) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        return plan;
    }
}