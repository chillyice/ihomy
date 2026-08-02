package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.Photo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PhotoMapper extends BaseMapper<Photo> {

    @Select("SELECT p.* FROM content_photo p "
            + "JOIN content_album a ON p.album_id = a.id "
            + "WHERE p.family_id = #{familyId} "
            + "AND p.visibility = 4 "
            + "AND a.type = 'public' "
            + "AND p.deleted = 0 AND a.deleted = 0 "
            + "ORDER BY p.created_at DESC "
            + "LIMIT #{limit}")
    List<Photo> selectPublicByFamily(Long familyId, int limit);
}
