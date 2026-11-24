package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.Family;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FamilyMapper extends BaseMapper<Family> {

    Family selectDefault();

    /** 照片 AI 标题描述总开关(家庭级,默认关闭) */
    Boolean photoCaptionEnabled(@Param("fid") long familyId);

    int setPhotoCaptionEnabled(@Param("fid") long familyId, @Param("enabled") boolean enabled);
}
