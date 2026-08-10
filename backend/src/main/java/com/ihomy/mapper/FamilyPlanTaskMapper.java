package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.PlanTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 计划子任务映射。
 */
@Mapper
public interface FamilyPlanTaskMapper extends BaseMapper<PlanTask> {
}