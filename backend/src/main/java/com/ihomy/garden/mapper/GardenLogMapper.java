package com.ihomy.garden.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.garden.entity.GardenLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 成长日志 Mapper:纯 MP 插入 + 按 op_id 查重(uk_op_id 兜底幂等);
 * 任务领取/技能分配/推阶用确定性合成 op_id 走同一唯一索引,不建新表(详细设计 §0-1)。
 * 日志保留 180 天,超期热度行由 GardenLogCleanup 分批物理删(自定义 XML,§15.2);
 * STAGE_ADVANCE / TASK_CLAIM / SKILL_ALLOCATE / SKILL_RESET / PLANT / HARVEST 为标记行,豁免清理。
 */
@Mapper
public interface GardenLogMapper extends BaseMapper<GardenLog> {

    /** 分批物理删:只清热度行(照料/拆信),标记行与 PLANT/HARVEST 豁免(§15.2 判定禁读日志的同源裁决) */
    int cleanupExpired(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);
}
