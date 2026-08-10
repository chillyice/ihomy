package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.Photo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface PhotoMapper extends BaseMapper<Photo> {

    List<Photo> selectPublicByFamily(@Param("familyId") Long familyId, @Param("limit") int limit);

    List<Photo> selectLatestByFamily(@Param("familyId") Long familyId, @Param("limit") int limit);

    List<Map<String, Object>> selectCascadeByFamily(@Param("familyId") Long familyId, @Param("userId") Long userId, @Param("limit") int limit);

    List<Photo> selectLatestPublicByFamily(@Param("familyId") Long familyId, @Param("limit") int limit);

    int deletePhysicalById(@Param("id") Long id);

    int deletePhysicalByAlbumId(@Param("albumId") Long albumId);
}
