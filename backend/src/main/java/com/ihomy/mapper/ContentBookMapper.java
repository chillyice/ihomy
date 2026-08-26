package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.ContentBook;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ContentBookMapper extends BaseMapper<ContentBook> {

    int incrViewCount(@Param("id") Long id);

    List<String> selectCategoriesByFamily(@Param("familyId") Long familyId);

    int renameCategory(@Param("familyId") Long familyId, @Param("oldName") String oldName, @Param("newName") String newName);

    int clearCategory(@Param("familyId") Long familyId, @Param("category") String category);

    int deletePhysicalById(@Param("id") Long id);

    // V7.2: multi-category relation operations
    List<Long> selectBookIdsByCategory(@Param("categoryId") Long categoryId);

    List<Long> selectCategoryIdsByBookId(@Param("bookId") Long bookId);

    int insertRel(@Param("bookId") Long bookId, @Param("categoryId") Long categoryId);

    int deleteRelByBookId(@Param("bookId") Long bookId);

    int deleteRelByCategory(@Param("categoryId") Long categoryId);
}
