package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.FamilyFile;
import org.apache.ibatis.annotations.Mapper;

/** 家庭文件(family_file)基础 CRUD,自定义 SQL 不进这里 */
@Mapper
public interface FamilyFileMapper extends BaseMapper<FamilyFile> {
}
