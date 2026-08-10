package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.PointsOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 兑换订单映射:兑换记录与核销状态。
 */
@Mapper
public interface PointsOrderMapper extends BaseMapper<PointsOrder> {
}
