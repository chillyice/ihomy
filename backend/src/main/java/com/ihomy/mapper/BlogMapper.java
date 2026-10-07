package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.Blog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 博客自定义 SQL(接口不写注解,全部在 resources/mapper/BlogMapper.xml)。
 * 分类相关方法成对出现,注意区分「置空分类」与「删整篇」两种语义,不可互换。
 */
@Mapper
public interface BlogMapper extends BaseMapper<Blog> {

    /** 浏览量原子自增(view_count = view_count + 1),不要在 Service 里读改写 */
    int incrViewCount(@Param("id") Long id);

    /** 家庭内已用过的分类去重列表(仅未删且分类非空),供筛选下拉 */
    List<String> selectCategoriesByFamily(@Param("familyId") Long familyId);

    /**
     * 按分类聚合篇数。非家长(isOwner=false)只统计自己可见的:
     * 自己写的 + 家庭/公开的,避免泄露他人私有博客的存在。
     */
    List<Map<String, Object>> selectCategoryCounts(@Param("familyId") Long familyId, @Param("authorId") Long authorId, @Param("isOwner") boolean isOwner);

    /** 重命名单个分类(精确匹配整段分类名) */
    int renameCategory(@Param("familyId") Long familyId, @Param("oldName") String oldName, @Param("newName") String newName);

    /** 按前缀批量重命名分类路径(如「生活/旅行」改「日常/旅行」时连同子分类一起改) */
    int renameCategoryPrefix(@Param("familyId") Long familyId, @Param("oldPrefix") String oldPrefix, @Param("newPrefix") String newPrefix);

    /** 解除分类归属:把该分类下的博客 category 置 NULL,博客本身保留 */
    int clearCategory(@Param("familyId") Long familyId, @Param("category") String category);

    /** 连博客一并删除:走原始 DELETE 绕过 @TableLogic,不进回收站、不可恢复 */
    int deleteByCategory(@Param("familyId") Long familyId, @Param("category") String category);
}
