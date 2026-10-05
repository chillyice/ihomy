package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.SysParameter;
import org.apache.ibatis.annotations.Param;

public interface SysParameterMapper extends BaseMapper<SysParameter> {

    /** 按 name 查参数(自定义 SQL 在 resources/mapper/SysParameterMapper.xml) */
    SysParameter findByName(@Param("name") String name);

    /** 按 name upsert:存在则更新 value,否则插入(用于 AES 盐值等系统参数) */
    int upsert(SysParameter param);
}
