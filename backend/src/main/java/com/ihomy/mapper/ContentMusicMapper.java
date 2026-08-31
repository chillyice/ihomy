package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.ContentMusic;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ContentMusicMapper extends BaseMapper<ContentMusic> {

    /** 物理删除(绕过全局 logic-delete,映射曲目删除/刷新 prune 用) */
    void deletePhysicalById(@Param("id") Long id);
}
