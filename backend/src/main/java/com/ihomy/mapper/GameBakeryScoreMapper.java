package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.GameBakeryScore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 小面包房家庭榜映射:上报走 upsert(一人一行覆盖),榜单走 XML join 成员表。
 */
@Mapper
public interface GameBakeryScoreMapper extends BaseMapper<GameBakeryScore> {

    /** 上报本人净资产:首次插入,之后覆盖数值(净值会因消费回落,不能取 MAX) */
    int upsert(@Param("familyId") Long familyId, @Param("userId") Long userId, @Param("score") Long score);

    /** 家庭榜前 N 名:只含当前家庭成员(退家庭的行不出现),同分按更早上榜在前 */
    List<Map<String, Object>> selectRanking(@Param("familyId") Long familyId, @Param("limit") int limit);

    /** 我在本家庭的榜单行(不在前 N 时用于补一行) */
    Map<String, Object> selectMyRow(@Param("familyId") Long familyId, @Param("userId") Long userId);
}
