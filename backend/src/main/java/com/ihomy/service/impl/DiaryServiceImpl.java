package com.ihomy.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.DiaryDTO;
import com.ihomy.entity.Diary;
import com.ihomy.mapper.DiaryMapper;
import com.ihomy.service.DiaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DiaryServiceImpl implements DiaryService {

    private final DiaryMapper diaryMapper;

    @Override
    public IPage<Diary> page(int current, int size, Long familyId, Long currentUserId, boolean isOwner) {
        LambdaQueryWrapper<Diary> qw = new LambdaQueryWrapper<>();
        if (familyId != null) {
            qw.eq(Diary::getFamilyId, familyId);
            if (isOwner) {
                // 家长可见全部
            } else {
                qw.and(w -> w.eq(Diary::getAuthorId, currentUserId)
                            .or().in(Diary::getVisibility, 3, 4));
            }
        } else {
            qw.eq(Diary::getVisibility, 4);
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
