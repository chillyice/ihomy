package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.FamilyGameRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 游戏完成记录映射:只做追加写入与 BaseMapper 查询,统计 SQL 在 FamilyAchievementMapper.xml。
 */
@Mapper
public interface FamilyGameRecordMapper extends BaseMapper<FamilyGameRecord> {
}
