package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.PointsProduct;
import org.apache.ibatis.annotations.Mapper;

/**
 * 积分商品映射:家长上架的家庭虚拟物品。
 */
@Mapper
public interface PointsProductMapper extends BaseMapper<PointsProduct> {
}
