package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.ContentMindmap;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ContentMindmapMapper extends BaseMapper<ContentMindmap> {

    /** 回收站列表(逻辑删数据,MP 常规查询不可见) */
    List<ContentMindmap> selectTrashByFamily(@Param("familyId") Long familyId);

    /** 查单条已删除记录 */
    ContentMindmap selectDeletedById(@Param("id") Long id);

    /** 恢复(逻辑删 → 正常) */
    int restoreById(@Param("id") Long id);

    /** 物理删除(MP deleteById 实为软删,回收站彻底删除必须走 XML DELETE) */
    int deletePhysicalById(@Param("id") Long id);
}
