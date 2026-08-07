package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.Photo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PhotoMapper extends BaseMapper<Photo> {

    List<Photo> selectPublicByFamily(@Param("familyId") Long familyId, @Param("limit") int limit);

    List<Photo> selectLatestByFamily(@Param("familyId") Long familyId, @Param("limit") int limit);

    List<Photo> selectLatestPublicByFamily(@Param("familyId") Long familyId, @Param("limit") int limit);
}
