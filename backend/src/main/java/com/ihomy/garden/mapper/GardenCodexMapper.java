package com.ihomy.garden.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.garden.entity.GardenCodex;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 图鉴 Mapper:upsert 计数——返回 1 = 首收(新行),返回 2 = 已存在累加(FR-18)。
 */
@Mapper
public interface GardenCodexMapper extends BaseMapper<GardenCodex> {

    int upsertHarvest(@Param("familyId") Long familyId, @Param("cropCode") String cropCode,
                      @Param("firstHarvestAt") LocalDateTime firstHarvestAt);
}
