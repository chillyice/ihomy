package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.ContentMindmapSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ContentMindmapSnapshotMapper extends BaseMapper<ContentMindmapSnapshot> {

    /** 列表(不含 data 大字段) */
    List<ContentMindmapSnapshot> selectListByMindmapId(@Param("mindmapId") Long mindmapId);

    /** 保留最近 N 份,更早的物理删(快照表无逻辑删,直接 DELETE) */
    int deleteKeepRecent(@Param("mindmapId") Long mindmapId, @Param("keep") int keep);
}
