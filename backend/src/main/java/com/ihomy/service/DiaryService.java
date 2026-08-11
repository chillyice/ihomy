package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ihomy.common.BizException;
import com.ihomy.common.DictConst;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.DiaryDTO;
import com.ihomy.entity.Diary;
import com.ihomy.mapper.DiaryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 生活日志(日记)业务:分页按家庭/可见范围过滤,增删改校验归属与权限。
 * 可见性:3=家庭可见,4=公开,0=仅自己。
 */
@Service
@RequiredArgsConstructor
public class DiaryService {

    private final DiaryMapper diaryMapper;
    private final PointsService pointsService;

    /** 分页查询:家长见全部,成员见自己的+家庭可见/公开,游客仅公开 */
    public IPage<Diary> page(int current, int size, Long familyId, Long currentUserId, boolean isOwner) {
        LambdaQueryWrapper<Diary> qw = new LambdaQueryWrapper<>();
        if (familyId != null) {
            qw.eq(Diary::getFamilyId, familyId);
            if (isOwner) {
                // 家长可见全部
            } else {
                qw.and(w -> w.eq(Diary::getAuthorId, currentUserId)
                            .or().in(Diary::getVisibility, DictConst.VIS_FAMILY, DictConst.VIS_PUBLIC));
            }
        } else {
            qw.eq(Diary::getVisibility, DictConst.VIS_PUBLIC);
        }
        qw.orderByDesc(Diary::getCreatedAt);
        return diaryMapper.selectPage(new Page<>(current, size), qw);
    }

    /** 新建日记:默认家庭可见 */
    public Diary create(Long authorId, Long familyId, DiaryDTO dto) {
        Diary diary = new Diary();
        diary.setContent(dto.getContent());
        diary.setMood(dto.getMood());
        diary.setWeather(dto.getWeather());
        diary.setAuthorId(authorId);
        diary.setFamilyId(familyId);
        diary.setVisibility(DictConst.visibility(dto.getVisibility()));
        diaryMapper.insert(diary);
        pointsService.addRecord(authorId, familyId, "REWARD", PointsService.REWARD_DIARY, "写日记");
        return diary;
    }

    /** 更新日记:仅作者本人可改 */
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
        if (dto.getVisibility() != null) diary.setVisibility(DictConst.visibility(dto.getVisibility()));
        diaryMapper.updateById(diary);
        return diary;
    }

    /** 删除日记:作者本人或家长可删 */
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
