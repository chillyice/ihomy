package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.FamilyHealthViewLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 健康数据查看审计映射:只增不改(轻量),查询用 MP 条件构造器。
 */
@Mapper
public interface FamilyHealthViewLogMapper extends BaseMapper<FamilyHealthViewLog> {
}
