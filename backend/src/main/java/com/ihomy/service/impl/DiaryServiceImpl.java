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

/**
 * 生活日志(日记)业务实现:分页按家庭/可见范围过滤,增删改校验归属与权限。
 * 可见性:3=家庭可见,4=公开,0=仅自己。
 */
@Service
@RequiredArgsConstructor
public class DiaryServiceImpl implements DiaryService {

    private final DiaryMapper diaryMapper;

    /** 分页查询:家长见全部,成员见自己的+家庭可见/公开,游客仅公开 */
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

    /** 新建日记:默认家庭可见 */
    @Override
    public Diary create(Long authorId, Long familyId, DiaryDTO dto) {
        Diary diary = new Diary();
        diary.setContent(dto.getContent());
        diary.setMood(dto.getMood());
        diary.setWeather(dto.getWeather());
        diary.setAuthorId(authorId);
        diary.setFamilyId(familyId);
        diary.setVisibility(dto.getVisibility() == null ? 3 : dto.getVisibility());
        diaryMapper.insert(diary);
        return diary;
    }

    /** 更新日记:仅作者本人可改 */
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

    /** 删除日记:作者本人或家长可删 */
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
