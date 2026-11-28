package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.HomesteadBuff;
import org.apache.ibatis.annotations.Mapper;

/** 小院慢生活料理 buff 映射:全部标准 CRUD(过期行按条件删除),无自定义 SQL。 */
@Mapper
public interface HomesteadBuffMapper extends BaseMapper<HomesteadBuff> {
}
