package com.ihomy.garden.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.garden.entity.GardenWorld;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

/**
 * 花园世界注册表 Mapper:每日信封的幂等领取走条件 UPDATE(0 行 = 当天已拆)。
 */
@Mapper
public interface GardenWorldMapper extends BaseMapper<GardenWorld> {

    /** 条件推进拆信日期:仅当 last_mail_date 为空或早于 date 时生效,返回影响行数 */
    int claimMailDay(@Param("familyId") Long familyId, @Param("date") LocalDate date);

    /** 家庭经济动作的行锁(推阶/技能分配/重置串行化,详细设计 §2.2/§6.3):事务内 FOR UPDATE */
    @Select("SELECT * FROM games.garden_world WHERE id = #{familyId} FOR UPDATE")
    GardenWorld lockById(@Param("familyId") Long familyId);
}
