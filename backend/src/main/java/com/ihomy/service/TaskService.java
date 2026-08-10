package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.DictConst;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.TaskDTO;
import com.ihomy.entity.SysUser;
import com.ihomy.entity.Task;
import com.ihomy.mapper.SysUserMapper;
import com.ihomy.mapper.TaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 悬赏任务业务:状态机 0待领取→1进行中→2待确认→3已完成/4已取消。
 * 领取限他人(发布者不可自领);确认结清由发布者操作,积分奖励实时入账 PointsService。
 */
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskMapper taskMapper;
    private final SysUserMapper sysUserMapper;
    private final PointsService pointsService;

/** 家庭任务列表(含发布人/领取人昵称),按最新发布排序 */
    public List<Map<String, Object>> list(Long familyId) {
        List<Task> tasks = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getFamilyId, familyId)
                .orderByDesc(Task::getCreatedAt));
        if (tasks.isEmpty()) return List.of();
        // 收集发布人与领取人两类用户 ID,一次性批量查昵称
        List<Long> ids = tasks.stream()
                .flatMap(t -> java.util.stream.Stream.of(t.getCreatedBy(), t.getAssigneeId()))
                .filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, String> names = sysUserMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getNickname));
        return tasks.stream().map(t -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", t.getId());
            map.put("title", t.getTitle());
            map.put("description", t.getDescription());
            map.put("rewardType", t.getRewardType());
            map.put("rewardPoints", t.getRewardPoints());
            map.put("rewardItem", t.getRewardItem());
            map.put("status", t.getStatus());
            map.put("createdBy", t.getCreatedBy());
            map.put("creatorName", names.getOrDefault(t.getCreatedBy(), "未知成员"));
            map.put("assigneeId", t.getAssigneeId());
            map.put("assigneeName", t.getAssigneeId() == null ? null
                    : names.getOrDefault(t.getAssigneeId(), "未知成员"));
            map.put("createdAt", t.getCreatedAt());
            return map;
        }).collect(Collectors.toList());
    }

    /** 发布任务:补全家庭与默认状态 */
    public Task create(Long userId, Long familyId, TaskDTO dto) {
        Task task = new Task();
        task.setFamilyId(familyId);
        task.setTitle(dto.getTitle());
        task.setDescription(dto.getDescription());
        task.setRewardType(DictConst.rewardType(dto.getRewardType()));
        task.setRewardPoints(dto.getRewardPoints() == null ? 0 : dto.getRewardPoints());
        task.setRewardItem(dto.getRewardItem());
        task.setStatus(DictConst.TASK_OPEN);
        task.setCreatedBy(userId);
        taskMapper.insert(task);
        return task;
    }

    /** 领取任务:仅他人可领,待领取状态才可接 */
    public void claim(Long taskId, Long familyId, Long userId) {
        Task task = requireTask(taskId, familyId);
        if (task.getCreatedBy().equals(userId)) {
            throw new BizException(ResultCode.BAD_REQUEST, "不能领取自己发布的任务");
        }
        if (!DictConst.TASK_OPEN.equals(task.getStatus())) {
            throw new BizException(ResultCode.CONFLICT, "任务已被领取或已结束");
        }
        task.setAssigneeId(userId);
        task.setStatus(DictConst.TASK_IN_PROGRESS);
        taskMapper.updateById(task);
    }

    /** 放弃领取:领取人交回任务,回到待领取 */
    public void abandon(Long taskId, Long familyId, Long userId) {
        Task task = requireTask(taskId, familyId);
        if (!userId.equals(task.getAssigneeId()) || !DictConst.TASK_IN_PROGRESS.equals(task.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "仅领取人可在进行中放弃任务");
        }
        task.setAssigneeId(null);
        task.setStatus(DictConst.TASK_OPEN);
        taskMapper.updateById(task);
    }

    /** 完成申报:领取人标记任务做完,等待发布者确认 */
    public void finish(Long taskId, Long familyId, Long userId) {
        Task task = requireTask(taskId, familyId);
        if (!userId.equals(task.getAssigneeId()) || !DictConst.TASK_IN_PROGRESS.equals(task.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "仅领取人可申报完成");
        }
        task.setStatus(DictConst.TASK_REVIEW);
        taskMapper.updateById(task);
    }

    /** 确认结算:发布者确认完成;积分奖励实时入账领取人 */
    @Transactional
    public void confirm(Long taskId, Long familyId, Long userId) {
        Task task = requireTask(taskId, familyId);
        if (!task.getCreatedBy().equals(userId) || !DictConst.TASK_REVIEW.equals(task.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "仅发布者可确认待确认任务");
        }
        task.setStatus(DictConst.TASK_DONE);
        taskMapper.updateById(task);
        if (DictConst.REWARD_POINTS.equals(task.getRewardType()) && task.getRewardPoints() != null && task.getRewardPoints() > 0) {
            pointsService.addRecord(task.getAssigneeId(), familyId, "REWARD",
                    task.getRewardPoints(), "完成任务【" + task.getTitle() + "】");
        }
    }

    /** 取消任务:发布者取消未终态任务(待领取/进行中/待确认) */
    public void cancel(Long taskId, Long familyId, Long userId) {
        Task task = requireTask(taskId, familyId);
if (!task.getCreatedBy().equals(userId)) {
            throw new BizException(ResultCode.BAD_REQUEST, "仅发布者可取消任务");
        }
        if (DictConst.TASK_DONE.equals(task.getStatus()) || DictConst.TASK_CANCELLED.equals(task.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "任务已结束,不可取消");
        }
        task.setStatus(DictConst.TASK_CANCELLED);
        taskMapper.updateById(task);
    }

    /** 取本家庭任务,不存在跨家庭返回 404 */
    private Task requireTask(Long taskId, Long familyId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null || !task.getFamilyId().equals(familyId)) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        return task;
    }
}