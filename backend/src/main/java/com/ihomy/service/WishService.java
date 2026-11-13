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
import com.ihomy.service.access.ContentAccessService;
import com.ihomy.service.access.ContentKind;
import com.ihomy.service.access.ViewerScope;
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
    private final ContentAccessService contentAccess;
    private final AudienceService audienceService;

    /** 愿望列表(带提出人昵称),待实现优先;游客只看演示家庭的公开愿望 */
    public List<Map<String, Object>> list(ViewerScope viewer) {
        if (viewer.familyId() == null) return List.of();
        LambdaQueryWrapper<Wish> qw = new LambdaQueryWrapper<>();
        contentAccess.applyVisible(qw, ContentKind.WISH, viewer,
                Wish::getFamilyId, Wish::getRequesterId, Wish::getVisibility, Wish::getId);
        return toMaps(wishMapper.selectList(qw
                .orderByAsc(Wish::getStatus)
                .orderByDesc(Wish::getCreatedAt)), viewer.familyId());
    }

    /** 愿望实体 → 视图对象(附提出人昵称;被分享的来源标 sharedFrom/readOnly) */
    private List<Map<String, Object>> toMaps(List<Wish> wishes, Long viewerFamilyId) {
        if (wishes.isEmpty()) return List.of();
        Map<Long, String> names = sysUserMapper.selectBatchIds(
                        wishes.stream().map(Wish::getRequesterId).collect(Collectors.toList())).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getNickname));
        Map<Long, Map<String, Object>> shared = audienceService.sharedSources(
                ContentKind.WISH, wishes.stream().map(Wish::getId).toList(), viewerFamilyId);
        return wishes.stream().map(w -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", w.getId());
            map.put("title", w.getTitle());
            map.put("reason", w.getReason());
            map.put("category", w.getCategory());
            map.put("sourceType", w.getSourceType());
            map.put("sourceId", w.getSourceId());
            map.put("sourceTitle", w.getSourceTitle());
            map.put("status", w.getStatus());
            map.put("requesterName", names.getOrDefault(w.getRequesterId(), "未知成员"));
            map.put("achievedAt", w.getAchievedAt());
            map.put("createdAt", w.getCreatedAt());
            map.put("familyId", w.getFamilyId());
            if (shared.containsKey(w.getId())) {
                map.put("readOnly", true);
                map.put("sharedFrom", shared.get(w.getId()));
            }
            return map;
        }).collect(Collectors.toList());
    }

/** 新增愿望:默认待实现、家庭可见 */
    public Wish create(Long userId, Long familyId, WishDTO dto) {
        if (dto.getTitle() == null || dto.getTitle().isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "请填写愿望名称");
        }
        Wish wish = new Wish();
        wish.setFamilyId(familyId);
        wish.setTitle(dto.getTitle());
        wish.setReason(dto.getReason());
        wish.setCategory(dto.getCategory());
        wish.setSourceType(requireSourceType(dto.getSourceType()));
        wish.setSourceId(blankToNull(dto.getSourceId()));
        wish.setSourceTitle(blankToNull(dto.getSourceTitle()));
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
        if (dto.getSourceType() != null) wish.setSourceType(requireSourceType(dto.getSourceType()));
        if (dto.getSourceId() != null) wish.setSourceId(blankToNull(dto.getSourceId()));
        if (dto.getSourceTitle() != null) wish.setSourceTitle(blankToNull(dto.getSourceTitle()));
        apply(wish);
        wishMapper.updateById(wish);
    }

    public void delete(Long id, Long familyId) {
        wishMapper.deleteById(require(id, familyId).getId());
        audienceService.revokeContent(ContentKind.WISH, id);
    }

    /** 状态联动达成时间:已实现(1)→记录/保留,未实现(0/2)→清空 */
    private void apply(Wish wish) {
        if (DictConst.WISH_ACHIEVED.equals(wish.getStatus())) {
            if (wish.getAchievedAt() == null) wish.setAchievedAt(LocalDateTime.now());
        } else {
            wish.setAchievedAt(null);
        }
    }

    /** 来源类型校验:空=直接添加;非空但不在字典内直接报错,不静默回退 */
    private String requireSourceType(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String source = DictConst.wishSource(raw);
        if (source == null) throw new BizException(ResultCode.BAD_REQUEST, "愿望来源不正确");
        return source;
    }

    private String blankToNull(String v) {
        if (v == null || v.isBlank()) return null;
        return v.trim();
    }

    private Wish require(Long id, Long familyId) {
        Wish wish = wishMapper.selectById(id);
        if (wish == null || !wish.getFamilyId().equals(familyId)) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        return wish;
    }
}