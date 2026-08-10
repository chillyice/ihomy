package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.DictConst;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.WishDTO;
import com.ihomy.entity.SysUser;
import com.ihomy.entity.Wish;
import com.ihomy.mapper.SysUserMapper;
import com.ihomy.mapper.WishMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 愿望单业务:家庭愿望增删改,标记实现时记录达成时间,改回待实现则清空达成时间。
 */
@Service
@RequiredArgsConstructor
public class WishService {

    private final WishMapper wishMapper;
    private final SysUserMapper sysUserMapper;

    /** 家庭愿望列表(带提出人昵称),待实现优先 */
    public List<Map<String, Object>> list(Long familyId) {
        List<Wish> wishes = wishMapper.selectList(new LambdaQueryWrapper<Wish>()
                .eq(Wish::getFamilyId, familyId)
                .orderByAsc(Wish::getStatus)
                .orderByDesc(Wish::getCreatedAt));
        if (wishes.isEmpty()) return List.of();
        Map<Long, String> names = sysUserMapper.selectBatchIds(
                        wishes.stream().map(Wish::getRequesterId).collect(Collectors.toList())).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getNickname));
        return wishes.stream().map(w -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", w.getId());
            map.put("title", w.getTitle());
            map.put("reason", w.getReason());
            map.put("category", w.getCategory());
            map.put("status", w.getStatus());
            map.put("requesterName", names.getOrDefault(w.getRequesterId(), "未知成员"));
            map.put("achievedAt", w.getAchievedAt());
            map.put("createdAt", w.getCreatedAt());
            return map;
        }).collect(Collectors.toList());
    }

/** 新增愿望:默认待实现、家庭可见 */
    public Wish create(Long userId, Long familyId, WishDTO dto) {
        Wish wish = new Wish();
        wish.setFamilyId(familyId);
        wish.setTitle(dto.getTitle());
        wish.setReason(dto.getReason());
        wish.setCategory(dto.getCategory());
        wish.setStatus(DictConst.wishStatus(dto.getStatus()));
        wish.setRequesterId(userId);
        wish.setVisibility(DictConst.VIS_FAMILY);
        apply(wish); // 新建即带已实现状态时记录达成时间
        wishMapper.insert(wish);
        return wish;
    }

    /** 编辑愿望;状态切到已实现记达成时间,改回未实现清空 */
    public void update(Long id, Long familyId, WishDTO dto) {
        Wish wish = require(id, familyId);
        if (dto.getTitle() != null) wish.setTitle(dto.getTitle());
        if (dto.getReason() != null) wish.setReason(dto.getReason());
        if (dto.getCategory() != null) wish.setCategory(dto.getCategory());
        if (dto.getStatus() != null) wish.setStatus(DictConst.wishStatus(dto.getStatus()));
        apply(wish);
        wishMapper.updateById(wish);
    }

    public void delete(Long id, Long familyId) {
        wishMapper.deleteById(require(id, familyId).getId());
    }

    /** 状态联动达成时间:已实现(1)→记录/保留,未实现(0/2)→清空 */
    private void apply(Wish wish) {
        if (DictConst.WISH_ACHIEVED.equals(wish.getStatus())) {
            if (wish.getAchievedAt() == null) wish.setAchievedAt(LocalDateTime.now());
        } else {
            wish.setAchievedAt(null);
        }
    }

    private Wish require(Long id, Long familyId) {
        Wish wish = wishMapper.selectById(id);
        if (wish == null || !wish.getFamilyId().equals(familyId)) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        return wish;
    }
}