package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.PointsRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 积分流水映射:自定义 SQL(余额汇总)放 XML,接口不写注解。
 */
@Mapper
public interface PointsRecordMapper extends BaseMapper<PointsRecord> {

    /** 统计某用户当前总积分(流水 change 之和) */
    Integer sumBalance(@Param("userId") Long userId);
}
