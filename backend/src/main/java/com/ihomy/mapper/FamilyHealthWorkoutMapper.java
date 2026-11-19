package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.FamilyHealthWorkout;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 训练记录映射:重复导入按唯一键 upsert 覆盖,批量写走 XML。
 */
@Mapper
public interface FamilyHealthWorkoutMapper extends BaseMapper<FamilyHealthWorkout> {

    int upsertBatch(@Param("list") List<FamilyHealthWorkout> list);

    /** 清除某成员某来源的全部训练记录(换设备/误配) */
    int deleteByProvider(@Param("userId") Long userId, @Param("provider") String provider);
}
