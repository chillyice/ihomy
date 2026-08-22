package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.Blog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BlogMapper extends BaseMapper<Blog> {

    int incrViewCount(@Param("id") Long id);

    List<String> selectCategoriesByFamily(@Param("familyId") Long familyId);

    int renameCategory(@Param("familyId") Long familyId, @Param("oldName") String oldName, @Param("newName") String newName);

    int clearCategory(@Param("familyId") Long familyId, @Param("category") String category);

    int deleteByCategory(@Param("familyId") Long familyId, @Param("category") String category);
}
