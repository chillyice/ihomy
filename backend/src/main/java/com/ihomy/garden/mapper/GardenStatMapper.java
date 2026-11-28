package com.ihomy.garden.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.garden.entity.GardenStat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.Map;

/**
 * 每日计数快照 Mapper:按日增量 upsert(计数 setSql 累加,项目规范)。
 */
@Mapper
public interface GardenStatMapper extends BaseMapper<GardenStat> {

    int upsertDeltas(@Param("familyId") Long familyId, @Param("statDate") LocalDate statDate,
                     @Param("care") int care, @Param("plant") int plant, @Param("harvest") int harvest,
                     @Param("coinIn") int coinIn, @Param("coinOut") int coinOut, @Param("xp") int xp);

    /** XP 总量:只读快照(§15.2 判定禁读日志) */
    @Select("SELECT COALESCE(SUM(xp_gain),0) FROM games.garden_stat WHERE family_id = #{familyId}")
    long sumXp(@Param("familyId") Long familyId);

    /** 任务条件/成就的累计快照:care/plant/harvest/coinIn 四项求和(单行) */
    @Select("SELECT COALESCE(SUM(care_cnt),0) AS careCnt, COALESCE(SUM(plant_cnt),0) AS plantCnt, "
            + "COALESCE(SUM(harvest_cnt),0) AS harvestCnt, COALESCE(SUM(coin_in),0) AS coinIn "
            + "FROM games.garden_stat WHERE family_id = #{familyId}")
    Map<String, Object> sumByFamily(@Param("familyId") Long familyId);

    /** 窗口版(中间任务/节气支线):自窗口起点(含)起求和 */
    @Select("SELECT COALESCE(SUM(care_cnt),0) AS careCnt, COALESCE(SUM(plant_cnt),0) AS plantCnt, "
            + "COALESCE(SUM(harvest_cnt),0) AS harvestCnt, COALESCE(SUM(coin_in),0) AS coinIn "
            + "FROM games.garden_stat WHERE family_id = #{familyId} AND stat_date >= #{since}")
    Map<String, Object> sumSince(@Param("familyId") Long familyId, @Param("since") LocalDate since);

    /** 成就「单日收获 ≥10」:历史单日最大收获数 */
    @Select("SELECT COALESCE(MAX(harvest_cnt),0) FROM games.garden_stat WHERE family_id = #{familyId}")
    int maxHarvestDay(@Param("familyId") Long familyId);
}
