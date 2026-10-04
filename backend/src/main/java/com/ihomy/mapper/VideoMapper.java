package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.Video;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface VideoMapper extends BaseMapper<Video> {
    int deletePhysicalById(@Param("id") Long id);

    // ---------- 回收站 ----------
    List<Video> selectTrashByFamily(@Param("familyId") Long familyId);

    Video selectDeletedById(@Param("id") Long id);

    List<Long> selectExpiredTrashIds(@Param("cutoff") LocalDateTime cutoff);

    int softDeleteById(@Param("id") Long id);

    int restoreById(@Param("id") Long id);
}
