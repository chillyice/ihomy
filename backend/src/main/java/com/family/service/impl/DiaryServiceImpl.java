package com.family.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.family.common.BizException;
import com.family.common.ResultCode;
import com.family.dto.DiaryDTO;
import com.family.entity.Diary;
import com.family.mapper.DiaryMapper;
import com.family.service.DiaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DiaryServiceImpl implements DiaryService {

    private final DiaryMapper diaryMapper;

    @Override
    public IPage<Diary> page(int current, int size, Long familyId, Long currentUserId, boolean isOwner) {
        LambdaQueryWrapper<Diary> qw = new LambdaQueryWrapper<>();
        qw.eq(familyId != null, Diary::getFamilyId, familyId);
        if (!isOwner) {
            qw.and(w -> w.eq(Diary::getAuthorId, currentUserId)
                        .or().eq(Diary::getVisibility, 1));
        }
        qw.orderByDesc(Diary::getCreatedAt);
        return diaryMapper.selectPage(new Page<>(current, size), qw);
    }

    @Override
    public Diary create(Long authorId, Long familyId, DiaryDTO dto) {
        Diary diary = new Diary();
        diary.setContent(dto.getContent());
        diary.setMood(dto.getMood());
        diary.setWeather(dto.getWeather());
        diary.setAuthorId(authorId);
        diary.setFamilyId(familyId);
        diary.setVisibility(dto.getVisibility() == null ? 0 : dto.getVisibility());
        diaryMapper.insert(diary);
        return diary;
    }

    @Override
    public Diary update(Long id, Long currentUserId, DiaryDTO dto) {
        Diary diary = diaryMapper.selectById(id);
        if (diary == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        if (!diary.getAuthorId().equals(currentUserId)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        diary.setContent(dto.getContent());
        diary.setMood(dto.getMood());
        diary.setWeather(dto.getWeather());
        if (dto.getVisibility() != null) diary.setVisibility(dto.getVisibility());
        diaryMapper.updateById(diary);
        return diary;
    }

    @Override
    public void delete(Long id, Long currentUserId, boolean isOwner) {
        Diary diary = diaryMapper.selectById(id);
        if (diary == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        if (!isOwner && !diary.getAuthorId().equals(currentUserId)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        diaryMapper.deleteById(id);
    }
}
