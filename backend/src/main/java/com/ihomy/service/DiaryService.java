package com.ihomy.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ihomy.dto.DiaryDTO;
import com.ihomy.entity.Diary;

/**
 * 生活日志服务接口:分页/增删改。
 */
public interface DiaryService {
    IPage<Diary> page(int current, int size, Long familyId, Long currentUserId, boolean isOwner);

    Diary create(Long authorId, Long familyId, DiaryDTO dto);

    Diary update(Long id, Long currentUserId, DiaryDTO dto);

    void delete(Long id, Long currentUserId, boolean isOwner);
}
