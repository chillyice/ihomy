package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.Video;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 放映厅(本地视频)自定义 SQL(全部在 resources/mapper/VideoMapper.xml)。
 * 本接口未标 @Mapper,由主类的 @MapperScan("com.ihomy.mapper") 统一扫描注册。
 * 删除两套:常规删除走 @TableLogic 软删;回收站的读/恢复/超期清理必须走自定义语句
 * (MP 常规查询自动过滤 deleted=1,读不到回收站里的行)。
 */
public interface VideoMapper extends BaseMapper<Video> {

    /** 物理删除单行(绕过 @TableLogic),仅在回收站「彻底删除」时调用,调用方须同时清理磁盘文件 */
    int deletePhysicalById(@Param("id") Long id);

    // ---------- 回收站(以下均绕过 @TableLogic,否则读不到 deleted=1 的行) ----------
    List<Video> selectTrashByFamily(@Param("familyId") Long familyId);

    Video selectDeletedById(@Param("id") Long id);

    /** 已超保留期的回收站条目 ID(每日定时物理清理用);cutoff = 当前时间 - 7 天 */
    List<Long> selectExpiredTrashIds(@Param("cutoff") LocalDateTime cutoff);

    /** 软删进回收站(置 deleted=1 并记 deleted_at),磁盘文件保留 */
    int softDeleteById(@Param("id") Long id);

    /** 从回收站恢复(置 deleted=0) */
    int restoreById(@Param("id") Long id);
}
