package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.Photo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 照片自定义 SQL(全部在 resources/mapper/PhotoMapper.xml)。
 * 删除两套:常规删除走 @TableLogic 软删;回收站的读/恢复/超期清理必须走自定义语句
 * (MP 常规查询自动过滤 deleted=1,读不到回收站里的行)。
 */
@Mapper
public interface PhotoMapper extends BaseMapper<Photo> {

    /** 家庭内最新公开照片(按时间倒序),供首页等公开位置使用 */
    List<Photo> selectLatestByFamily(@Param("familyId") Long familyId, @Param("limit") int limit);

    /**
     * 照片瀑布:按可见性过滤后随机取样。
     * 私有照片只对上传者本人可见(userId 即当前登录用户,驱动 PRIVATE 行的过滤);
     * 随机序见 XML 的 ponytail 说明。
     */
    List<Map<String, Object>> selectCascadeByFamily(@Param("familyId") Long familyId, @Param("userId") Long userId, @Param("limit") int limit);

    /** 仅公开照片(游客/壁纸等无登录态场景) */
    List<Photo> selectLatestPublicByFamily(@Param("familyId") Long familyId, @Param("limit") int limit);

    /** 物理删除单行(绕过 @TableLogic),仅在回收站「彻底删除」时调用,调用方须同时清理磁盘文件 */
    int deletePhysicalById(@Param("id") Long id);

    /** 物理删除整本相册下的全部照片,随相册彻底删除时调用 */
    int deletePhysicalByAlbumId(@Param("albumId") Long albumId);

    /** 批量统计各相册的照片数,返回 { albumId, cnt };避免逐相册 selectCount 造成 N+1 */
    List<Map<String, Object>> countByAlbumIds(@Param("ids") List<Long> ids);

    // ---------- 回收站(以下均绕过 @TableLogic,否则读不到 deleted=1 的行) ----------
    List<Photo> selectTrashByFamily(@Param("familyId") Long familyId);

    Photo selectDeletedById(@Param("id") Long id);

    /** 已超保留期的回收站条目 ID(每日定时物理清理用);cutoff = 当前时间 - 7 天 */
    List<Long> selectExpiredTrashIds(@Param("cutoff") java.time.LocalDateTime cutoff);

    /** 软删进回收站(置 deleted=1 并记 deleted_at),磁盘文件保留 */
    int softDeleteById(@Param("id") Long id);

    /** 相册软删时连同其照片一起进回收站 */
    int softDeleteByAlbumIds(@Param("ids") List<Long> ids);

    /** 从回收站恢复(置 deleted=0) */
    int restoreById(@Param("id") Long id);

    /** 相册恢复时连同其照片一起恢复 */
    int restoreByAlbumIds(@Param("ids") List<Long> ids);

    /** 取相册子树内全部照片(含已删),彻底删除时连文件一并清理 */
    List<Photo> selectByAlbumIdsAny(@Param("ids") List<Long> ids);
}
