package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.FamilyPlan;
import org.apache.ibatis.annotations.Mapper;

/**
 * 家庭计划映射:子任务完成度在 FamilyPlanService 汇总。
 */
@Mapper
public interface FamilyPlanMapper extends BaseMapper<FamilyPlan> {
}