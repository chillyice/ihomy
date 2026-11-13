package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.DictConst;
import com.ihomy.common.ResultCode;
import com.ihomy.controller.PublicController;
import com.ihomy.dto.CircleDTO;
import com.ihomy.entity.Family;
import com.ihomy.entity.FamilyCircle;
import com.ihomy.entity.FamilyCircleMember;
import com.ihomy.entity.FamilyShareDefault;
import com.ihomy.entity.SysUser;
import com.ihomy.mapper.ContentAudienceMapper;
import com.ihomy.mapper.FamilyCircleMapper;
import com.ihomy.mapper.FamilyCircleMemberMapper;
import com.ihomy.mapper.FamilyMapper;
import com.ihomy.mapper.FamilyShareDefaultMapper;
import com.ihomy.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 亲友圈业务:建圈/邀请/加入/改称呼/退出/解散。
 *
 * <p>铁律:入圈不共享任何内容——本类只维护「谁和谁可以互相分享」的关系,内容可见性仍由
 * {@link com.ihomy.service.access.ContentAccessService} 与 content_audience 决定。
 * 圈内只暴露家庭名/封面/称呼,不暴露成员名单与家谱。
 */
@Service
@RequiredArgsConstructor
public class CircleService {

    /** 一个家庭同时最多加入几个圈 */
    public static final int MAX_CIRCLES_PER_FAMILY = 3;
    private static final int INVITE_DAYS = 7;
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final FamilyCircleMapper circleMapper;
    private final FamilyCircleMemberMapper memberMapper;
    private final ContentAudienceMapper audienceMapper;
    private final FamilyMapper familyMapper;
    private final FamilyShareDefaultMapper shareDefaultMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PublicController publicController;

    /** 建圈:发起家庭自动入圈,自己是圈管理员 */
    @Transactional
    public Map<String, Object> create(SysUser user, Long familyId, CircleDTO dto) {
        Family family = requireFamily(familyId);
        requireCircleEnabled(family);
        long joined = memberMapper.selectCount(new LambdaQueryWrapper<FamilyCircleMember>()
                .eq(FamilyCircleMember::getFamilyId, familyId)
                .eq(FamilyCircleMember::getStatus, DictConst.CIRCLE_MEMBER_ACTIVE));
        if (joined >= MAX_CIRCLES_PER_FAMILY) {
            throw new BizException(ResultCode.BAD_REQUEST, "最多加入 " + MAX_CIRCLES_PER_FAMILY + " 个亲友圈");
        }
        FamilyCircle circle = new FamilyCircle();
        circle.setName(dto.getName().trim());
        circle.setCoverImage(dto.getCoverImage());
        circle.setOwnerFamilyId(familyId);
        circle.setMaxFamilies(6);
        circle.setStatus(DictConst.CIRCLE_ACTIVE);
        circle.setCreatedBy(user.getId());
        circleMapper.insert(circle);

        FamilyCircleMember me = new FamilyCircleMember();
        me.setCircleId(circle.getId());
        me.setFamilyId(familyId);
        me.setJoinedAt(LocalDateTime.now());
        me.setStatus(DictConst.CIRCLE_MEMBER_ACTIVE);
        memberMapper.insert(me);

        Map<String, Object> view = circleView(circle, familyId);
        view.put("inviteCode", circle.getInviteCode());
        return view;
    }

    /** 当前家庭所在圈(只显示自己的圈;切家庭后随当前家庭变化) */
    public List<Map<String, Object>> list(Long familyId) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (familyId == null) return result;
        List<FamilyCircleMember> mine = memberMapper.selectList(new LambdaQueryWrapper<FamilyCircleMember>()
                .eq(FamilyCircleMember::getFamilyId, familyId)
                .eq(FamilyCircleMember::getStatus, DictConst.CIRCLE_MEMBER_ACTIVE));
        if (mine.isEmpty()) return result;
        Map<Long, FamilyCircle> circles = circleMapper.selectBatchIds(
                        mine.stream().map(FamilyCircleMember::getCircleId).toList()).stream()
                .filter(c -> DictConst.CIRCLE_ACTIVE.equals(c.getStatus()))
                .collect(Collectors.toMap(FamilyCircle::getId, c -> c));
        for (FamilyCircleMember m : mine) {
            FamilyCircle c = circles.get(m.getCircleId());
            if (c == null) continue;
            Map<String, Object> view = circleView(c, familyId);
            view.put("myAlias", m.getAlias());
            if (familyId.equals(c.getOwnerFamilyId())) {
                view.put("inviteCode", c.getInviteCode());
                view.put("inviteExpiresAt", c.getInviteExpiresAt());
            }
            result.add(view);
        }
        result.sort((a, b) -> Long.compare((Long) b.get("id"), (Long) a.get("id")));
        return result;
    }

    /** 生成/重置邀请码:只有发起家庭可以邀请 */
    @Transactional
    public Map<String, Object> invite(Long circleId, Long familyId) {
        FamilyCircle circle = requireOwner(circleId, familyId);
        String code = nextCode();
        LocalDateTime expires = LocalDateTime.now().plusDays(INVITE_DAYS);
        circleMapper.update(null, new LambdaUpdateWrapper<FamilyCircle>()
                .eq(FamilyCircle::getId, circle.getId())
                .set(FamilyCircle::getInviteCode, code)
                .set(FamilyCircle::getInviteExpiresAt, expires));
        Map<String, Object> view = new HashMap<>();
        view.put("code", code);
        view.put("expiresAt", expires);
        view.put("circleId", circle.getId());
        return view;
    }

    /** 凭邀请码加入:已在圈内则幂等返回;退出过的家庭重新入圈恢复原行 */
    @Transactional
    public Map<String, Object> join(String code, Long familyId, SysUser user) {
        Family family = requireFamily(familyId);
        requireCircleEnabled(family);
        String normalized = code == null ? "" : code.trim().toUpperCase();
        if (normalized.isEmpty()) throw new BizException(ResultCode.BAD_REQUEST, "请填写邀请码");
        FamilyCircle circle = circleMapper.selectOne(new LambdaQueryWrapper<FamilyCircle>()
                .eq(FamilyCircle::getInviteCode, normalized)
                .eq(FamilyCircle::getStatus, DictConst.CIRCLE_ACTIVE));
        if (circle == null) throw new BizException(ResultCode.NOT_FOUND);
        if (circle.getInviteExpiresAt() != null && circle.getInviteExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BizException(ResultCode.BAD_REQUEST, "邀请码已过期,请让对方重新生成");
        }
        if (familyId.equals(circle.getOwnerFamilyId())) {
            throw new BizException(ResultCode.BAD_REQUEST, "这是你自己发起的亲友圈");
        }

        FamilyCircleMember existing = memberMapper.selectOne(new LambdaQueryWrapper<FamilyCircleMember>()
                .eq(FamilyCircleMember::getCircleId, circle.getId())
                .eq(FamilyCircleMember::getFamilyId, familyId));
        if (existing != null && DictConst.CIRCLE_MEMBER_ACTIVE.equals(existing.getStatus())) {
            return circleView(circle, familyId);
        }
        long families = memberMapper.selectCount(new LambdaQueryWrapper<FamilyCircleMember>()
                .eq(FamilyCircleMember::getCircleId, circle.getId())
                .eq(FamilyCircleMember::getStatus, DictConst.CIRCLE_MEMBER_ACTIVE));
        if (families >= (circle.getMaxFamilies() == null ? 6 : circle.getMaxFamilies())) {
            throw new BizException(ResultCode.BAD_REQUEST, "这个亲友圈已经满了");
        }
        long joined = memberMapper.selectCount(new LambdaQueryWrapper<FamilyCircleMember>()
                .eq(FamilyCircleMember::getFamilyId, familyId)
                .eq(FamilyCircleMember::getStatus, DictConst.CIRCLE_MEMBER_ACTIVE));
        if (joined >= MAX_CIRCLES_PER_FAMILY) {
            throw new BizException(ResultCode.BAD_REQUEST, "最多加入 " + MAX_CIRCLES_PER_FAMILY + " 个亲友圈");
        }

        if (existing == null) {
            FamilyCircleMember member = new FamilyCircleMember();
            member.setCircleId(circle.getId());
            member.setFamilyId(familyId);
            member.setInvitedByFamilyId(circle.getOwnerFamilyId());
            member.setJoinedAt(LocalDateTime.now());
            member.setStatus(DictConst.CIRCLE_MEMBER_ACTIVE);
            memberMapper.insert(member);
        } else {
            memberMapper.update(null, new LambdaUpdateWrapper<FamilyCircleMember>()
                    .eq(FamilyCircleMember::getId, existing.getId())
                    .set(FamilyCircleMember::getStatus, DictConst.CIRCLE_MEMBER_ACTIVE)
                    .set(FamilyCircleMember::getJoinedAt, LocalDateTime.now()));
        }
        return circleView(circle, familyId);
    }

    /** 改圈名/封面:只有发起家庭 */
    public void update(Long circleId, Long familyId, CircleDTO dto) {
        requireOwner(circleId, familyId);
        circleMapper.update(null, new LambdaUpdateWrapper<FamilyCircle>()
                .eq(FamilyCircle::getId, circleId)
                .set(FamilyCircle::getName, dto.getName().trim())
                .set(FamilyCircle::getCoverImage, dto.getCoverImage()));
    }

    /** 本家庭在这个圈里的称呼(空=清除) */
    public void setAlias(Long circleId, Long familyId, String alias) {
        requireMember(circleId, familyId);
        memberMapper.update(null, new LambdaUpdateWrapper<FamilyCircleMember>()
                .eq(FamilyCircleMember::getCircleId, circleId)
                .eq(FamilyCircleMember::getFamilyId, familyId)
                .set(FamilyCircleMember::getAlias, alias));
    }

    /** 移除某个家庭:仅发起家庭;同时收回这个圈分享给该家庭的内容 */
    @Transactional
    public void removeFamily(Long circleId, Long targetFamilyId, Long familyId) {
        requireOwner(circleId, familyId);
        if (familyId.equals(targetFamilyId)) {
            throw new BizException(ResultCode.BAD_REQUEST, "请用「解散」结束自己的圈");
        }
        FamilyCircleMember target = memberMapper.selectOne(new LambdaQueryWrapper<FamilyCircleMember>()
                .eq(FamilyCircleMember::getCircleId, circleId)
                .eq(FamilyCircleMember::getFamilyId, targetFamilyId));
        if (target == null) throw new BizException(ResultCode.NOT_FOUND);
        memberMapper.update(null, new LambdaUpdateWrapper<FamilyCircleMember>()
                .eq(FamilyCircleMember::getId, target.getId())
                .set(FamilyCircleMember::getStatus, DictConst.CIRCLE_MEMBER_LEFT));
        revokeByCircle(circleId, targetFamilyId);
    }

    /** 本家庭退出:保留历史行(可被重新邀请),收回发给自己的内容 */
    @Transactional
    public void leave(Long circleId, Long familyId) {
        requireMember(circleId, familyId);
        memberMapper.update(null, new LambdaUpdateWrapper<FamilyCircleMember>()
                .eq(FamilyCircleMember::getCircleId, circleId)
                .eq(FamilyCircleMember::getFamilyId, familyId)
                .set(FamilyCircleMember::getStatus, DictConst.CIRCLE_MEMBER_LEFT));
        revokeByCircle(circleId, familyId);
    }

    /** 解散:仅发起家庭;圈置 CLOSED 并收回该圈展开出去的全部分享 */
    @Transactional
    public void disband(Long circleId, Long familyId) {
        requireOwner(circleId, familyId);
        circleMapper.update(null, new LambdaUpdateWrapper<FamilyCircle>()
                .eq(FamilyCircle::getId, circleId)
                .set(FamilyCircle::getStatus, DictConst.CIRCLE_CLOSED)
                .set(FamilyCircle::getInviteCode, null)
                .set(FamilyCircle::getInviteExpiresAt, null));
        // 成员行一并置为已退出:否则已解散的圈仍占着「最多加入 N 个圈」的名额
        memberMapper.update(null, new LambdaUpdateWrapper<FamilyCircleMember>()
                .eq(FamilyCircleMember::getCircleId, circleId)
                .eq(FamilyCircleMember::getStatus, DictConst.CIRCLE_MEMBER_ACTIVE)
                .set(FamilyCircleMember::getStatus, DictConst.CIRCLE_MEMBER_LEFT));
        revokeByCircle(circleId, null);
    }

    /** 本家庭在圈内可选的家庭(去重,带圈名与称呼),供「默认分享目标」与可见性面板共用 */
    public List<Map<String, Object>> targetFamilies(Long familyId) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (familyId == null) return result;
        List<FamilyCircleMember> mine = memberMapper.selectList(new LambdaQueryWrapper<FamilyCircleMember>()
                .eq(FamilyCircleMember::getFamilyId, familyId)
                .eq(FamilyCircleMember::getStatus, DictConst.CIRCLE_MEMBER_ACTIVE));
        if (mine.isEmpty()) return result;
        Map<Long, FamilyCircle> circles = circleMapper.selectBatchIds(
                        mine.stream().map(FamilyCircleMember::getCircleId).toList()).stream()
                .filter(c -> DictConst.CIRCLE_ACTIVE.equals(c.getStatus()))
                .collect(Collectors.toMap(FamilyCircle::getId, c -> c));
        Set<Long> seen = new java.util.HashSet<>();
        for (FamilyCircleMember m : mine) {
            FamilyCircle circle = circles.get(m.getCircleId());
            if (circle == null) continue;
            List<FamilyCircleMember> members = memberMapper.selectList(new LambdaQueryWrapper<FamilyCircleMember>()
                    .eq(FamilyCircleMember::getCircleId, circle.getId())
                    .eq(FamilyCircleMember::getStatus, DictConst.CIRCLE_MEMBER_ACTIVE)
                    .orderByAsc(FamilyCircleMember::getId));
            Map<Long, Family> families = memberFamilyMap(members);
            for (FamilyCircleMember other : members) {
                if (other.getFamilyId().equals(familyId) || !seen.add(other.getFamilyId())) continue;
                Family f = families.get(other.getFamilyId());
                Map<String, Object> fm = new LinkedHashMap<>();
                fm.put("familyId", other.getFamilyId());
                fm.put("familyName", f == null ? null : f.getName());
                fm.put("familyCoverImage", f == null ? null : f.getCoverImage());
                fm.put("alias", other.getAlias());
                fm.put("circleId", circle.getId());
                fm.put("circleName", circle.getName());
                result.add(fm);
            }
        }
        return result;
    }

    /** 默认分享目标:家庭的预勾选家庭 id + 可选家庭列表(读设置页与发布面板共用) */
    public Map<String, Object> shareDefaults(Long familyId) {
        Set<Long> targets = shareDefaultMapper.selectList(new LambdaQueryWrapper<FamilyShareDefault>()
                        .eq(FamilyShareDefault::getFamilyId, familyId)).stream()
                .map(FamilyShareDefault::getTargetFamilyId)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("targetFamilyIds", new ArrayList<>(targets));
        data.put("families", targetFamilies(familyId));
        return data;
    }

    /** 保存默认分享目标:只接受当前仍在同一活跃圈里的家庭,先清后写(整体覆盖) */
    @Transactional
    public Map<String, Object> saveShareDefaults(Long familyId, List<Long> targetFamilyIds, Long userId) {
        Set<Long> available = targetFamilies(familyId).stream()
                .map(m -> ((Number) m.get("familyId")).longValue())
                .collect(Collectors.toSet());
        List<Long> wanted = targetFamilyIds == null ? List.of() : targetFamilyIds.stream()
                .filter(Objects::nonNull).distinct().toList();
        for (Long id : wanted) {
            if (!available.contains(id)) {
                throw new BizException(ResultCode.BAD_REQUEST, "只能选择同一亲友圈里的家庭");
            }
        }
        shareDefaultMapper.delete(new LambdaQueryWrapper<FamilyShareDefault>()
                .eq(FamilyShareDefault::getFamilyId, familyId));
        for (Long id : wanted) {
            FamilyShareDefault row = new FamilyShareDefault();
            row.setFamilyId(familyId);
            row.setTargetFamilyId(id);
            row.setCreatedBy(userId);
            shareDefaultMapper.insert(row);
        }
        return shareDefaults(familyId);
    }

    /** 某家庭当前在圈的家庭 id(除自己),供分享时展开 */
    public Set<Long> siblingFamilyIds(Long circleId, Long selfFamilyId) {
        return memberMapper.selectList(new LambdaQueryWrapper<FamilyCircleMember>()
                        .eq(FamilyCircleMember::getCircleId, circleId)
                        .eq(FamilyCircleMember::getStatus, DictConst.CIRCLE_MEMBER_ACTIVE)).stream()
                .map(FamilyCircleMember::getFamilyId)
                .filter(id -> !id.equals(selfFamilyId))
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    /** 圈内家庭概要(家庭名/封面/称呼),不返回成员列表 */
    private Map<String, Object> circleView(FamilyCircle circle, Long selfFamilyId) {
        List<FamilyCircleMember> members = memberMapper.selectList(new LambdaQueryWrapper<FamilyCircleMember>()
                .eq(FamilyCircleMember::getCircleId, circle.getId())
                .eq(FamilyCircleMember::getStatus, DictConst.CIRCLE_MEMBER_ACTIVE)
                .orderByAsc(FamilyCircleMember::getId));
        Map<Long, Family> families = memberFamilyMap(members);
        List<Map<String, Object>> familiesView = new ArrayList<>();
        for (FamilyCircleMember m : members) {
            Family f = families.get(m.getFamilyId());
            Map<String, Object> fm = new LinkedHashMap<>();
            fm.put("familyId", m.getFamilyId());
            fm.put("familyName", f == null ? null : f.getName());
            fm.put("familyCoverImage", f == null ? null : f.getCoverImage());
            fm.put("alias", m.getAlias());
            fm.put("joinedAt", m.getJoinedAt());
            fm.put("self", m.getFamilyId().equals(selfFamilyId));
            fm.put("owner", m.getFamilyId().equals(circle.getOwnerFamilyId()));
            familiesView.add(fm);
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", circle.getId());
        view.put("name", circle.getName());
        view.put("coverImage", circle.getCoverImage());
        view.put("ownerFamilyId", circle.getOwnerFamilyId());
        view.put("mine", circle.getOwnerFamilyId().equals(selfFamilyId));
        view.put("maxFamilies", circle.getMaxFamilies());
        view.put("status", circle.getStatus());
        view.put("families", familiesView);
        return view;
    }

    /** 批量取家庭信息,免逐行 selectById(N+1) */
    private Map<Long, Family> memberFamilyMap(Collection<FamilyCircleMember> members) {
        Set<Long> ids = members.stream().map(FamilyCircleMember::getFamilyId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return familyMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Family::getId, f -> f));
    }

    /** 收回该圈分享出去的内容(退出/被移除时只收回发给该家庭的),并清相关家庭首页缓存 */
    private void revokeByCircle(Long circleId, Long targetFamilyId) {
        List<Long> owners = audienceMapper.selectOwnerFamilyIdsByCircle(circleId, targetFamilyId);
        audienceMapper.deleteByCircle(circleId, targetFamilyId);
        owners.forEach(publicController::invalidateHomeCache);
        if (targetFamilyId != null) publicController.invalidateHomeCache(targetFamilyId);
    }

    private FamilyCircle requireOwner(Long circleId, Long familyId) {
        FamilyCircle circle = circleMapper.selectById(circleId);
        if (circle == null || !DictConst.CIRCLE_ACTIVE.equals(circle.getStatus())) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        if (!circle.getOwnerFamilyId().equals(familyId)) {
            throw new BizException(ResultCode.FORBIDDEN, "只有发起这个圈的家庭可以操作");
        }
        return circle;
    }

    private FamilyCircleMember requireMember(Long circleId, Long familyId) {
        FamilyCircle circle = circleMapper.selectById(circleId);
        if (circle == null || !DictConst.CIRCLE_ACTIVE.equals(circle.getStatus())) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        FamilyCircleMember m = memberMapper.selectOne(new LambdaQueryWrapper<FamilyCircleMember>()
                .eq(FamilyCircleMember::getCircleId, circleId)
                .eq(FamilyCircleMember::getFamilyId, familyId)
                .eq(FamilyCircleMember::getStatus, DictConst.CIRCLE_MEMBER_ACTIVE));
        if (m == null) throw new BizException(ResultCode.NOT_FOUND);
        return m;
    }

    private Family requireFamily(Long familyId) {
        Family family = familyId == null ? null : familyMapper.selectById(familyId);
        if (family == null) throw new BizException(ResultCode.NOT_FOUND);
        return family;
    }

    private void requireCircleEnabled(Family family) {
        if (family.getCircleEnabled() != null && family.getCircleEnabled() == 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "这个家庭没有开启亲友圈");
        }
    }

    private String nextCode() {
        for (int attempt = 0; attempt < 5; attempt++) {
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 8; i++) sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
            String code = sb.toString();
            if (circleMapper.selectCount(new LambdaQueryWrapper<FamilyCircle>()
                    .eq(FamilyCircle::getInviteCode, code)) == 0) {
                return code;
            }
        }
        throw new BizException(ResultCode.BAD_REQUEST, "邀请码生成失败,请重试");
    }
}
