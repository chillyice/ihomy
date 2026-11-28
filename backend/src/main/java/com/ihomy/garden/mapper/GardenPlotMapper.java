package com.ihomy.garden.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.garden.entity.GardenPlot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

import java.time.LocalDateTime;

/**
 * 花园地块 Mapper:播种 / 备盆 / 照料 / 收获全部条件 UPDATE(0 行即失败,架构分册 §17.1),
 * 不用「先查后写」;地块归属(owner_uid)是统一前置校验的数据来源。
 */
@Mapper
public interface GardenPlotMapper extends BaseMapper<GardenPlot> {

    /** 补地块行(世界引导 / 成员加入):唯一键去重 */
    int insertIgnore(@Param("familyId") Long familyId, @Param("ownerUid") String ownerUid,
                     @Param("venue") String venue, @Param("slotNo") Integer slotNo,
                     @Param("soilType") String soilType, @Param("createdBy") String createdBy);

    /** 播种:仅空地可种;收获遗留字段一并复位 */
    int sow(@Param("id") Long id, @Param("familyId") Long familyId, @Param("cropCode") String cropCode,
            @Param("plantedAt") LocalDateTime plantedAt);

    /** 备盆:一次性,已备返回 0 行(FR-04a) */
    int prepare(@Param("id") Long id, @Param("familyId") Long familyId);

    /** 浇水:12h 地块冷却 + 累计加成(FR-07) */
    int water(@Param("id") Long id, @Param("familyId") Long familyId,
              @Param("now") LocalDateTime now, @Param("boundary") LocalDateTime boundary,
              @Param("boost") int boost);

    /** 晒太阳:同浇水 */
    int sun(@Param("id") Long id, @Param("familyId") Long familyId,
            @Param("now") LocalDateTime now, @Param("boundary") LocalDateTime boundary,
            @Param("boost") int boost);

    /** 施肥:同浇水 + 落 last_fertilized_at */
    int fertilize(@Param("id") Long id, @Param("familyId") Long familyId,
                  @Param("now") LocalDateTime now, @Param("boundary") LocalDateTime boundary,
                  @Param("boost") int boost);

    /** 收获:锁定已种地块并清空照料状态(FR-12/FR-14),返回 0 行 = 重复收获或并发冲突 */
    int harvest(@Param("id") Long id, @Param("familyId") Long familyId, @Param("cropCode") String cropCode);

    /** 该家庭已备盆的地块数(首盆免单判定,FR-04a) */
    int countPrepared(@Param("familyId") Long familyId);

    /** 该家庭地块总行数(= 已解锁地块;仓库容量按此现算,FR-13) */
    int countAll(@Param("familyId") Long familyId);

    /** 照料 XP 日去重(每地块每日第一次 +5,写时判定):命中 1 行 = 今日首次,准发 XP */
    int grantCareXpDay(@Param("id") Long id, @Param("familyId") Long familyId, @Param("today") LocalDate today);
}
