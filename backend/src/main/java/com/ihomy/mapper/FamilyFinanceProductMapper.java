package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.FamilyFinanceProduct;
import org.apache.ibatis.annotations.Mapper;

/**
 * 家庭理财持仓 Mapper:只做单表 CRUD,自定义 SQL 一律放 resources/mapper/*.xml。
 */
@Mapper
public interface FamilyFinanceProductMapper extends BaseMapper<FamilyFinanceProduct> {
}
