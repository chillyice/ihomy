package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.ContentLike;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ContentLikeMapper extends BaseMapper<ContentLike> {

    long countByContent(@Param("contentType") String contentType, @Param("contentId") Long contentId);
}
