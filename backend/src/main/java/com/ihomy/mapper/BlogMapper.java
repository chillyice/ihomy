package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.Blog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface BlogMapper extends BaseMapper<Blog> {

    int incrViewCount(@Param("id") Long id);

    List<String> selectCategoriesByFamily(@Param("familyId") Long familyId);

    List<Map<String, Object>> selectCategoryCounts(@Param("familyId") Long familyId, @Param("authorId") Long authorId, @Param("isOwner") boolean isOwner);

    int renameCategory(@Param("familyId") Long familyId, @Param("oldName") String oldName, @Param("newName") String newName);

    int renameCategoryPrefix(@Param("familyId") Long familyId, @Param("oldPrefix") String oldPrefix, @Param("newPrefix") String newPrefix);

    int clearCategory(@Param("familyId") Long familyId, @Param("category") String category);

    int deleteByCategory(@Param("familyId") Long familyId, @Param("category") String category);
}
