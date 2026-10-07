package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.ContentBook;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 书架(电子书)自定义 SQL(全部在 resources/mapper/ContentBookMapper.xml)。
 * 删除分两套:常规删除走 @TableLogic 软删;回收站的列表/恢复/超期清理必须走本接口的
 * 自定义语句(MP 常规查询会自动过滤 deleted=1,读不到回收站里的行)。
 */
@Mapper
public interface ContentBookMapper extends BaseMapper<ContentBook> {

    /** 阅读量原子自增(view_count = view_count + 1),避免读改写丢更新 */
    int incrViewCount(@Param("id") Long id);

    /** 家庭内已用过的分类去重列表(仅未删且非空),供筛选 */
    List<String> selectCategoriesByFamily(@Param("familyId") Long familyId);

    /** 重命名单个分类(精确匹配整段分类名) */
    int renameCategory(@Param("familyId") Long familyId, @Param("oldName") String oldName, @Param("newName") String newName);

    /** 解除分类归属:把该分类下的书籍 category 置 NULL,书籍本身保留 */
    int clearCategory(@Param("familyId") Long familyId, @Param("category") String category);

    /** 物理删除单行(绕过 @TableLogic),仅在回收站「彻底删除」时调用,调用方须同时清理磁盘文件 */
    int deletePhysicalById(@Param("id") Long id);

    // ---------- V7.2 多分类关系表 content_book_category_rel(一本书可属多个分类) ----------
    /** 某分类下的书籍 ID 列表 */
    List<Long> selectBookIdsByCategory(@Param("categoryId") Long categoryId);

    /** 某本书所属的分类 ID 列表(编辑回显用) */
    List<Long> selectCategoryIdsByBookId(@Param("bookId") Long bookId);

    /** 建立书-分类关联(多对多中间表) */
    int insertRel(@Param("bookId") Long bookId, @Param("categoryId") Long categoryId);

    /** 清空某本书的全部分类关联(改分类前先删后插) */
    int deleteRelByBookId(@Param("bookId") Long bookId);

    /** 删除某分类时先清掉其关联行,避免中间表留下悬空引用 */
    int deleteRelByCategory(@Param("categoryId") Long categoryId);

    // ---------- 回收站(以下均绕过 @TableLogic,否则读不到 deleted=1 的行) ----------
    List<ContentBook> selectTrashByFamily(@Param("familyId") Long familyId);

    ContentBook selectDeletedById(@Param("id") Long id);

    /** 已超保留期的回收站条目 ID(每日定时物理清理用);cutoff = 当前时间 - 7 天 */
    List<Long> selectExpiredTrashIds(@Param("cutoff") LocalDateTime cutoff);

    /** 软删进回收站(置 deleted=1 并记 deleted_at),磁盘文件保留 */
    int softDeleteById(@Param("id") Long id);

    /** 从回收站恢复(置 deleted=0) */
    int restoreById(@Param("id") Long id);
}
