package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.DictConst;
import com.ihomy.common.ResultCode;
import com.ihomy.controller.PublicController;
import com.ihomy.entity.Album;
import com.ihomy.entity.Blog;
import com.ihomy.entity.ContentAudience;
import com.ihomy.entity.ContentBook;
import com.ihomy.entity.Diary;
import com.ihomy.entity.Family;
import com.ihomy.entity.FamilyCircle;
import com.ihomy.entity.FamilyCircleMember;
import com.ihomy.entity.Photo;
import com.ihomy.entity.Video;
import com.ihomy.entity.Wish;
import com.ihomy.entity.SysRole;
import com.ihomy.entity.SysUser;
import com.ihomy.entity.SysUserRole;
import com.ihomy.mapper.AlbumMapper;
import com.ihomy.mapper.BlogMapper;
import com.ihomy.mapper.ContentAudienceMapper;
import com.ihomy.mapper.ContentBookMapper;
import com.ihomy.mapper.DiaryMapper;
import com.ihomy.mapper.FamilyCircleMapper;
import com.ihomy.mapper.FamilyCircleMemberMapper;
import com.ihomy.mapper.FamilyMapper;
import com.ihomy.mapper.PhotoMapper;
import com.ihomy.mapper.SysRoleMapper;
import com.ihomy.mapper.SysUserRoleMapper;
import com.ihomy.mapper.VideoMapper;
import com.ihomy.mapper.WishMapper;
import com.ihomy.service.access.ContentAccessService;
import com.ihomy.service.access.ContentKind;
import com.ihomy.service.access.ViewerScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 内容分享受众:把「给谁看」落成 content_audience 的逐家庭行。
 *
 * <p>铁律:默认只给自家人(不写任何受众行);分享是发布/编辑时的显式动作;
 * 单向可撤回(只有发送家庭能改/收回);接收家庭只读,拿不到目录、搜索与成员名单。
 * 圈在写入时展开为接收家庭,之后成员变更不影响已发出的分享(新家庭入圈看不到历史)。
 */
@Service
@RequiredArgsConstructor
public class AudienceService {

    /** 单条内容最多同时分享到几个圈 */
    private static final int MAX_CIRCLES = 3;

    private final ContentAudienceMapper audienceMapper;
    private final FamilyCircleMapper circleMapper;
    private final FamilyCircleMemberMapper memberMapper;
    private final PhotoMapper photoMapper;
    private final AlbumMapper albumMapper;
    private final BlogMapper blogMapper;
    private final DiaryMapper diaryMapper;
    private final VideoMapper videoMapper;
    private final WishMapper wishMapper;
    private final ContentBookMapper bookMapper;
    private final FamilyMapper familyMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper sysRoleMapper;
    private final NotificationService notificationService;
    private final ContentAccessService contentAccess;
    private final PublicController publicController;

    /**
     * 「给谁看」面板数据:可选圈(含圈内其他家庭)+ 当前已选中的圈。
     * 只有自家内容才有这个面板,跨家庭内容一律 NOT_FOUND。
     */
    public Map<String, Object> view(ContentKind kind, Long contentId, ViewerScope viewer) {
        Shareable content = requireOwn(kind, contentId, viewer);
        Family family = familyMapper.selectById(content.familyId());
        List<ContentAudience> rows = audienceMapper.selectList(new LambdaQueryWrapper<ContentAudience>()
                .eq(ContentAudience::getContentType, kind.code())
                .eq(ContentAudience::getContentId, contentId));
        Set<Long> selected = rows.stream().map(ContentAudience::getSourceCircleId)
                .filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("contentType", kind.code());
        data.put("contentId", contentId);
        data.put("selectedCircleIds", new ArrayList<>(selected));
        data.put("circles", myCircles(content.familyId()));
        data.put("targets", audienceMapper.selectTargets(kind.code(), contentId));
        data.put("canShare", contentAccess.canShare(kind, content.familyId(), content.authorId(),
                content.visibility(), viewer, family == null ? null : family.getSharePolicy()));
        return data;
    }

    /**
     * 改分享范围:传空=只给自家人(全部收回)。
     * 旧目标撤回、新目标写入,并对涉及的每个家庭失效首页缓存(接收方首页会多/少一条)。
     */
    @Transactional
    public Map<String, Object> set(ContentKind kind, Long contentId, List<Long> circleIds,
                                   SysUser user, ViewerScope viewer) {
        if (viewer.familyId() == null || viewer.userId() == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        Shareable content = requireOwn(kind, contentId, viewer);
        Family family = familyMapper.selectById(content.familyId());
        String policy = family == null ? null : family.getSharePolicy();
        if (!contentAccess.canShare(kind, content.familyId(), content.authorId(), content.visibility(), viewer, policy)) {
            throw new BizException(ResultCode.FORBIDDEN, DictConst.SHARE_OFF.equals(policy)
                    ? "家里关闭了对外分享" : "只有内容作者本人可以分享");
        }

        List<Long> wanted = circleIds == null ? List.of() : circleIds.stream()
                .filter(Objects::nonNull).distinct().toList();
        if (wanted.size() > MAX_CIRCLES) {
            throw new BizException(ResultCode.BAD_REQUEST, "最多同时分享到 " + MAX_CIRCLES + " 个亲友圈");
        }
        Map<Long, FamilyCircle> circles = validateCircles(wanted, content.familyId());

        // 圈 → 接收家庭(排除自己家):同一个家庭命中多个圈时按第一个圈记账
        Map<Long, Long> targetToCircle = new LinkedHashMap<>();
        for (Long circleId : wanted) {
            List<FamilyCircleMember> members = memberMapper.selectList(new LambdaQueryWrapper<FamilyCircleMember>()
                    .eq(FamilyCircleMember::getCircleId, circleId)
                    .eq(FamilyCircleMember::getStatus, DictConst.CIRCLE_MEMBER_ACTIVE));
            for (FamilyCircleMember m : members) {
                if (m.getFamilyId().equals(content.familyId())) continue;
                targetToCircle.putIfAbsent(m.getFamilyId(), circleId);
            }
        }

        Set<Long> oldTargets = audienceMapper.selectList(new LambdaQueryWrapper<ContentAudience>()
                        .eq(ContentAudience::getContentType, kind.code())
                        .eq(ContentAudience::getContentId, contentId)).stream()
                .map(ContentAudience::getTargetId).collect(Collectors.toCollection(LinkedHashSet::new));

        audienceMapper.delete(new LambdaQueryWrapper<ContentAudience>()
                .eq(ContentAudience::getContentType, kind.code())
                .eq(ContentAudience::getContentId, contentId));

        for (Map.Entry<Long, Long> e : targetToCircle.entrySet()) {
            ContentAudience row = new ContentAudience();
            row.setContentType(kind.code());
            row.setContentId(contentId);
            row.setOwnerFamilyId(content.familyId());
            row.setTargetType(DictConst.AUDIENCE_FAMILY);
            row.setTargetId(e.getKey());
            row.setSourceCircleId(e.getValue());
            row.setGrantedBy(user.getId());
            audienceMapper.insert(row);
        }

        Set<Long> cacheFamilies = new LinkedHashSet<>();
        cacheFamilies.add(content.familyId());
        cacheFamilies.addAll(oldTargets);
        cacheFamilies.addAll(targetToCircle.keySet());
        cacheFamilies.forEach(publicController::invalidateHomeCache);

        notifyNewTargets(kind, contentId, targetToCircle.keySet(), oldTargets, content.familyId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("targetCount", targetToCircle.size());
        result.put("targets", audienceMapper.selectTargets(kind.code(), contentId));
        return result;
    }

    /** 家庭分享记录(审计 + 一键收回用) */
    public Map<String, Object> audit(Long familyId, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 50);
        int safePage = Math.max(page, 1);
        long total = audienceMapper.countAudit(familyId);
        List<Map<String, Object>> rows = audienceMapper.selectAudit(familyId, (long) (safePage - 1) * safeSize, safeSize);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", total);
        data.put("page", safePage);
        data.put("size", safeSize);
        data.put("items", rows);
        return data;
    }

    /** 全部收回:清空本家庭分享出去的所有条目 */
    @Transactional
    public int revokeAll(Long familyId) {
        List<Long> oldTargets = audienceMapper.selectList(new LambdaQueryWrapper<ContentAudience>()
                        .eq(ContentAudience::getOwnerFamilyId, familyId)).stream()
                .map(ContentAudience::getTargetId).distinct().toList();
        int deleted = audienceMapper.delete(new LambdaQueryWrapper<ContentAudience>()
                .eq(ContentAudience::getOwnerFamilyId, familyId));
        oldTargets.forEach(publicController::invalidateHomeCache);
        publicController.invalidateHomeCache(familyId);
        return deleted;
    }

    /** 内容被删除/移入回收站时收起它的分享行,接收方随即看不到(首页缓存同步失效) */
    @Transactional
    public void revokeContent(ContentKind kind, Long contentId) {
        if (contentId == null) return;
        List<ContentAudience> rows = audienceMapper.selectList(new LambdaQueryWrapper<ContentAudience>()
                .eq(ContentAudience::getContentType, kind.code())
                .eq(ContentAudience::getContentId, contentId));
        if (rows.isEmpty()) return;
        audienceMapper.delete(new LambdaQueryWrapper<ContentAudience>()
                .eq(ContentAudience::getContentType, kind.code())
                .eq(ContentAudience::getContentId, contentId));
        rows.stream().map(ContentAudience::getTargetId).distinct()
                .forEach(publicController::invalidateHomeCache);
    }

    /** 单条收回(详情页「不再分享」) */
    @Transactional
    public void revokeOne(ContentKind kind, Long contentId, ViewerScope viewer) {
        requireOwn(kind, contentId, viewer);
        List<ContentAudience> rows = audienceMapper.selectList(new LambdaQueryWrapper<ContentAudience>()
                .eq(ContentAudience::getContentType, kind.code())
                .eq(ContentAudience::getContentId, contentId));
        if (rows.isEmpty()) return;
        audienceMapper.delete(new LambdaQueryWrapper<ContentAudience>()
                .eq(ContentAudience::getContentType, kind.code())
                .eq(ContentAudience::getContentId, contentId));
        rows.stream().map(ContentAudience::getTargetId).distinct()
                .forEach(publicController::invalidateHomeCache);
        publicController.invalidateHomeCache(viewer.familyId());
    }

    /** 内容是否分享给了指定家庭(详情放行判定用) */
    public boolean sharedTo(ContentKind kind, Long contentId, Long familyId) {
        if (contentId == null || familyId == null) return false;
        return audienceMapper.countSharedTo(kind.code(), contentId, familyId) > 0;
    }

    /** 分享给当前家庭的条目 id(相册列表混入被分享条目用) */
    public List<Long> sharedIds(ContentKind kind, Long familyId) {
        if (familyId == null) return List.of();
        return audienceMapper.selectList(new LambdaQueryWrapper<ContentAudience>()
                        .eq(ContentAudience::getContentType, kind.code())
                        .eq(ContentAudience::getTargetType, DictConst.AUDIENCE_FAMILY)
                        .eq(ContentAudience::getTargetId, familyId)).stream()
                .map(ContentAudience::getContentId).distinct().toList();
    }

    /** 列表批量回填 sharedFrom(contentId → 来源家庭),禁 N+1 */
    public Map<Long, Map<String, Object>> sharedSources(ContentKind kind, List<Long> contentIds, Long viewerFamilyId) {
        if (contentIds == null || contentIds.isEmpty() || viewerFamilyId == null) return Map.of();
        return audienceMapper.selectSharedSources(kind.code(), viewerFamilyId, contentIds).stream()
                .collect(Collectors.toMap(m -> ((Number) m.get("contentId")).longValue(), m -> m, (a, b) -> a));
    }

    /** 本家庭可选的圈(含圈内其他家庭的家庭名/称呼),给「给谁看」面板用 */
    private List<Map<String, Object>> myCircles(Long familyId) {
        List<FamilyCircleMember> mine = memberMapper.selectList(new LambdaQueryWrapper<FamilyCircleMember>()
                .eq(FamilyCircleMember::getFamilyId, familyId)
                .eq(FamilyCircleMember::getStatus, DictConst.CIRCLE_MEMBER_ACTIVE));
        if (mine.isEmpty()) return List.of();
        Map<Long, FamilyCircle> circles = circleMapper.selectBatchIds(
                        mine.stream().map(FamilyCircleMember::getCircleId).toList()).stream()
                .filter(c -> DictConst.CIRCLE_ACTIVE.equals(c.getStatus()))
                .collect(Collectors.toMap(FamilyCircle::getId, c -> c));
        List<Map<String, Object>> result = new ArrayList<>();
        for (FamilyCircleMember m : mine) {
            FamilyCircle circle = circles.get(m.getCircleId());
            if (circle == null) continue;
            List<FamilyCircleMember> members = memberMapper.selectList(new LambdaQueryWrapper<FamilyCircleMember>()
                    .eq(FamilyCircleMember::getCircleId, circle.getId())
                    .eq(FamilyCircleMember::getStatus, DictConst.CIRCLE_MEMBER_ACTIVE));
            List<Long> familyIds = members.stream().map(FamilyCircleMember::getFamilyId)
                    .filter(id -> !id.equals(familyId)).toList();
            Map<Long, Family> families = familyIds.isEmpty() ? Map.of()
                    : familyMapper.selectBatchIds(familyIds).stream().collect(Collectors.toMap(Family::getId, f -> f));
            List<Map<String, Object>> others = new ArrayList<>();
            for (FamilyCircleMember other : members) {
                if (other.getFamilyId().equals(familyId)) continue;
                Family f = families.get(other.getFamilyId());
                Map<String, Object> fm = new LinkedHashMap<>();
                fm.put("familyId", other.getFamilyId());
                fm.put("familyName", f == null ? null : f.getName());
                fm.put("alias", other.getAlias());
                others.add(fm);
            }
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("id", circle.getId());
            view.put("name", circle.getName());
            view.put("families", others);
            result.add(view);
        }
        return result;
    }

    /** 圈必须是当前家庭所在的活跃圈 */
    private Map<Long, FamilyCircle> validateCircles(List<Long> circleIds, Long familyId) {
        if (circleIds.isEmpty()) return Map.of();
        Map<Long, FamilyCircle> circles = circleMapper.selectBatchIds(circleIds).stream()
                .filter(c -> DictConst.CIRCLE_ACTIVE.equals(c.getStatus()))
                .collect(Collectors.toMap(FamilyCircle::getId, c -> c));
        if (circles.size() != circleIds.size()) throw new BizException(ResultCode.NOT_FOUND);
        Set<Long> myCircleIds = memberMapper.selectList(new LambdaQueryWrapper<FamilyCircleMember>()
                        .eq(FamilyCircleMember::getFamilyId, familyId)
                        .eq(FamilyCircleMember::getStatus, DictConst.CIRCLE_MEMBER_ACTIVE)).stream()
                .map(FamilyCircleMember::getCircleId).collect(Collectors.toSet());
        for (Long id : circleIds) {
            if (!myCircleIds.contains(id)) throw new BizException(ResultCode.NOT_FOUND);
        }
        return circles;
    }

    /** 只给新目标家庭发提醒(不重复打扰已经收到过的家庭) */
    private void notifyNewTargets(ContentKind kind, Long contentId, Set<Long> newTargets,
                                 Set<Long> oldTargets, Long ownerFamilyId) {
        List<Long> fresh = newTargets.stream().filter(id -> !oldTargets.contains(id)).toList();
        if (fresh.isEmpty()) return;
        Family owner = familyMapper.selectById(ownerFamilyId);
        String ownerName = owner == null ? "家人" : owner.getName();
        String what = contentLabel(kind);
        for (Long familyId : fresh) {
            for (Long userId : adultUserIds(familyId)) {
                notificationService.createInFamily(userId, familyId, "share",
                        "「" + ownerName + "」分享了" + what + "给你", contentId, kind.code(), contentId);
            }
        }
    }

    /** 提醒文案里的内容叫法(用户语言,不出现内部类型码) */
    private String contentLabel(ContentKind kind) {
        return switch (kind) {
            case ALBUM -> "相册";
            case PHOTO -> "照片";
            case BLOG -> "一篇博客";
            case DIARY -> "一篇日记";
            case VIDEO -> "一部影片";
            case WISH -> "一个愿望";
            case BOOK -> "一本书";
            default -> "内容";
        };
    }

    /** 家庭里的成人成员(OWNER/MEMBER),儿童与访客不在分享提醒范围内 */
    private List<Long> adultUserIds(Long familyId) {
        List<Long> roleIds = sysRoleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                        .in(SysRole::getRoleCode, "OWNER", "MEMBER")).stream()
                .map(SysRole::getId).toList();
        if (roleIds.isEmpty()) return List.of();
        return userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getFamilyId, familyId)
                        .in(SysUserRole::getRoleId, roleIds)).stream()
                .map(SysUserRole::getUserId).distinct().toList();
    }

    /** 当前家庭的内容才有分享范围;跨家庭一律 NOT_FOUND(不泄露存在性) */
    private Shareable requireOwn(ContentKind kind, Long contentId, ViewerScope viewer) {
        Shareable content = load(kind, contentId);
        if (content == null || viewer.familyId() == null || !viewer.familyId().equals(content.familyId())) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        return content;
    }

    /** 载入可分享内容的基本信息(相册/照片的可见性口径与可见性判定同一套) */
    private Shareable load(ContentKind kind, Long contentId) {
        if (kind == ContentKind.PHOTO) {
            Photo p = photoMapper.selectById(contentId);
            return p == null ? null : new Shareable(p.getId(), p.getFamilyId(), p.getAuthorId(), p.getVisibility());
        }
        if (kind == ContentKind.ALBUM) {
            Album a = albumMapper.selectById(contentId);
            if (a == null) return null;
            String visibility = "private".equals(a.getType()) ? DictConst.VIS_PRIVATE : DictConst.VIS_FAMILY;
            return new Shareable(a.getId(), a.getFamilyId(), a.getCreatedBy(), visibility);
        }
        if (kind == ContentKind.BLOG) {
            Blog b = blogMapper.selectById(contentId);
            return b == null ? null : new Shareable(b.getId(), b.getFamilyId(), b.getAuthorId(), b.getVisibility());
        }
        if (kind == ContentKind.DIARY) {
            Diary d = diaryMapper.selectById(contentId);
            return d == null ? null : new Shareable(d.getId(), d.getFamilyId(), d.getAuthorId(), d.getVisibility());
        }
        if (kind == ContentKind.VIDEO) {
            Video v = videoMapper.selectById(contentId);
            return v == null ? null : new Shareable(v.getId(), v.getFamilyId(), v.getUploaderId(), v.getVisibility());
        }
        if (kind == ContentKind.WISH) {
            Wish w = wishMapper.selectById(contentId);
            return w == null ? null : new Shareable(w.getId(), w.getFamilyId(), w.getRequesterId(), w.getVisibility());
        }
        if (kind == ContentKind.BOOK) {
            ContentBook b = bookMapper.selectById(contentId);
            return b == null ? null : new Shareable(b.getId(), b.getFamilyId(), b.getUploaderId(), b.getVisibility());
        }
        throw new BizException(ResultCode.BAD_REQUEST, "这个内容暂时还不能分享到亲友圈");
    }

    /** 可分享内容的最小信息 */
    private record Shareable(Long id, Long familyId, Long authorId, String visibility) {
    }
}
