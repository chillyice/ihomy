package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.Album;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AlbumMapper extends BaseMapper<Album> {
    int deletePhysicalById(@Param("id") Long id);

    // ---------- 回收站 ----------
    /** 回收站顶层相册(父相册不在回收站内,避免子树重复展示) */
    List<Album> selectTrashRoots(@Param("familyId") Long familyId);

    /** 家庭全部相册(含已删,回收站子树遍历用,不走逻辑删过滤) */
    List<Album> selectAllByFamily(@Param("familyId") Long familyId);

    Album selectDeletedById(@Param("id") Long id);

    List<Long> selectExpiredTrashIds(@Param("cutoff") LocalDateTime cutoff);

    int softDeleteById(@Param("id") Long id);

    int softDeleteByIds(@Param("ids") List<Long> ids);

    int restoreByIds(@Param("ids") List<Long> ids);
}
