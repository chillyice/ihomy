package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.Video;
import org.apache.ibatis.annotations.Param;

public interface VideoMapper extends BaseMapper<Video> {
    int deletePhysicalById(@Param("id") Long id);
}
