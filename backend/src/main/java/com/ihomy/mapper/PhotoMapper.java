package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.Photo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface PhotoMapper extends BaseMapper<Photo> {

    List<Photo> selectLatestByFamily(@Param("familyId") Long familyId, @Param("limit") int limit);

    List<Map<String, Object>> selectCascadeByFamily(@Param("familyId") Long familyId, @Param("userId") Long userId, @Param("limit") int limit);

    List<Photo> selectLatestPublicByFamily(@Param("familyId") Long familyId, @Param("limit") int limit);

    int deletePhysicalById(@Param("id") Long id);

    int deletePhysicalByAlbumId(@Param("albumId") Long albumId);

    List<Map<String, Object>> countByAlbumIds(@Param("ids") List<Long> ids);

    // ---------- 回收站 ----------
    List<Photo> selectTrashByFamily(@Param("familyId") Long familyId);

    Photo selectDeletedById(@Param("id") Long id);

    List<Long> selectExpiredTrashIds(@Param("cutoff") java.time.LocalDateTime cutoff);

    int softDeleteById(@Param("id") Long id);

    int softDeleteByAlbumIds(@Param("ids") List<Long> ids);

    int restoreById(@Param("id") Long id);

    int restoreByAlbumIds(@Param("ids") List<Long> ids);

    /** 取相册子树内全部照片(含已删),彻底删除时连文件一并清理 */
    List<Photo> selectByAlbumIdsAny(@Param("ids") List<Long> ids);
}
