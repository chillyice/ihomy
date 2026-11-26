package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.FamilyAchievement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 成就映射:指标聚合与解锁写入走 XML;普通查询复用 BaseMapper。
 */
@Mapper
public interface FamilyAchievementMapper extends BaseMapper<FamilyAchievement> {

    /** 一次取回本人的全部成就指标(别名与 AchievementCatalog 的指标键一致) */
    Map<String, Object> loadMetrics(@Param("userId") Long userId);

    /** 解锁一行;UNIQUE(user_id, code) 冲突时忽略,返回影响行数(1=本次新解锁) */
    int insertIgnore(@Param("userId") Long userId, @Param("familyId") Long familyId,
                     @Param("code") String code, @Param("score") int score);

    /** 家庭成就榜:以当前家庭角色绑定为成员口径,避免多角色行重复计数 */
    List<Map<String, Object>> selectRanking(@Param("familyId") Long familyId, @Param("limit") int limit);
}
