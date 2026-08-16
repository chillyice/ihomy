package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.SysParameter;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SysParameterMapper extends BaseMapper<SysParameter> {

    @Select("SELECT name, value, description, created_at, updated_at FROM sys_parameter WHERE name = #{name}")
    SysParameter findByName(@Param("name") String name);

    @Insert("INSERT INTO sys_parameter (name, value, description) VALUES (#{name}, #{value}, #{description}) ON DUPLICATE KEY UPDATE value = #{value}")
    int upsert(SysParameter param);
}
