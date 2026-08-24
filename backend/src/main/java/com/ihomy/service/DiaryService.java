package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ihomy.common.BizException;
import com.ihomy.common.DictConst;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.DiaryDTO;
import com.ihomy.entity.Diary;
import com.ihomy.entity.SysUser;
import com.ihomy.mapper.DiaryMapper;
import com.ihomy.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 生活日志(日记)业务:分页按家庭/可见范围过滤,增删改校验归属与权限。
 * 可见性:3=家庭可见,4=公开,0=仅自己。
 */
@Service
@RequiredArgsConstructor
public class DiaryService {

    private final DiaryMapper diaryMapper;
    private final SysUserMapper sysUserMapper;
    private final PointsService pointsService;

    /** 查询单条日记详情 */
    public Diary getById(Long id) {
        return diaryMapper.selectById(id);
    }

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
        IPage<Diary> page = diaryMapper.selectPage(new Page<>(current, size), qw);
        List<Diary> records = page.getRecords();
        if (!records.isEmpty()) {
            Set<Long> authorIds = records.stream().map(Diary::getAuthorId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
            if (!authorIds.isEmpty()) {
                Map<Long, String> nameMap = sysUserMapper.selectBatchIds(authorIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, u -> u.getNickname() != null ? u.getNickname() : u.getUsername(), (a, b) -> a));
                records.forEach(d -> d.setAuthorName(nameMap.get(d.getAuthorId())));
            }
        }
        return page;
    }

    /** 新建日记:默认仅自己可见 */
    public Diary create(Long authorId, Long familyId, DiaryDTO dto) {
        Diary diary = new Diary();
        diary.setContent(dto.getContent());
        diary.setMood(dto.getMood());
        diary.setWeather(dto.getWeather());
        diary.setImages(dto.getImages());
        diary.setAuthorId(authorId);
        diary.setFamilyId(familyId);
        diary.setVisibility(DictConst.visibility(dto.getVisibility()));
        if (dto.getDate() != null && !dto.getDate().isBlank()) {
            diary.setCreatedAt(java.time.LocalDateTime.parse(dto.getDate() + "T00:00:00"));
        }
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
        diary.setImages(dto.getImages());
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
