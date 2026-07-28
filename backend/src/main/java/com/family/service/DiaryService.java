package com.family.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.family.dto.DiaryDTO;
import com.family.entity.Diary;

public interface DiaryService {
    IPage<Diary> page(int current, int size, Long familyId, Long currentUserId, boolean isOwner);

    Diary create(Long authorId, Long familyId, DiaryDTO dto);

    Diary update(Long id, Long currentUserId, DiaryDTO dto);

    void delete(Long id, Long currentUserId, boolean isOwner);
}
