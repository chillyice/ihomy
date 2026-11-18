package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.FamilyHealthSleepStage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 睡眠分期映射:随所属会话重算物理删/批量插(派生数据)。
 */
@Mapper
public interface FamilyHealthSleepStageMapper extends BaseMapper<FamilyHealthSleepStage> {

    int deleteBySleepIds(@Param("sleepIds") List<Long> sleepIds);

    int deleteBySleepId(@Param("sleepId") Long sleepId);

    int insertBatch(@Param("list") List<FamilyHealthSleepStage> list);

    int deleteByProvider(@Param("userId") Long userId, @Param("provider") String provider);
}
