package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.Family;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FamilyMapper extends BaseMapper<Family> {

    @Select("SELECT * FROM sys_family_info WHERE is_default = 1 AND deleted = 0 LIMIT 1")
    Family selectDefault();
}
