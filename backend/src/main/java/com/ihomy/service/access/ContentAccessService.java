package com.ihomy.service.access;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.ihomy.common.BizException;
import com.ihomy.common.DictConst;
import com.ihomy.common.ResultCode;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 内容可见性判定的唯一来源:列表过滤、详情/点赞/评论校验、分享入口判定都走这里。
 *
 * <p>口径:
 * <ul>
 *   <li>游客:只读基准家庭(未登录时=默认演示家庭)里 {@code visibility=PUBLIC} 的内容;</li>
 *   <li>家长(OWNER):本家庭全部内容;</li>
 *   <li>家庭成员:本家庭 + (自己创建的 OR 家庭/公开可见);PRIVATE 只对作者本人与家长可见;</li>
 *   <li>跨家庭:除 PUBLIC 外一律不可见,调用方抛 NOT_FOUND(不泄露存在性)。</li>
 * </ul>
 *
 * <p>原则:可见性只在本类判定,Service 只负责把条件接到查询上;新增可见性档位只改这里。
 * 本类无依赖,可直接单测(见 ContentAccessServiceTest)。
 */
@Service
public class ContentAccessService {

    /** 家庭成员之间可见的档位:PRIVATE 只对作者本人与家长 */
    public List<String> memberVisibilities() {
        return List.of(DictConst.VIS_FAMILY, DictConst.VIS_PUBLIC);
    }

    /**
     * 列表可见范围(单点):家庭维度 + 个人/公开可见性。
     * 生成 {@code family_id = ? AND (author_id = ? OR visibility IN ('FAMILY','PUBLIC'))} 形态,
     * 单表查询、分页语义不变(不引入 UNION)。
     */
    public <T> void applyVisible(LambdaQueryWrapper<T> qw, ContentKind kind, ViewerScope viewer,
                                 SFunction<T, ?> familyCol, SFunction<T, ?> authorCol, SFunction<T, ?> visibilityCol) {
        applyVisible(qw, kind, viewer, familyCol, authorCol, visibilityCol, null);
    }

    /**
     * 同上,并额外放宽「被分享」这一档:{@code OR id IN (SELECT content_id FROM content_audience ...)}。
     * 只有可分享的内容类型(照片/相册)才传 {@code idCol};受众人数按
     * {@code idx_target(target_type,target_id,content_type,content_id)} 命中,分页语义不变。
     */
    public <T> void applyVisible(LambdaQueryWrapper<T> qw, ContentKind kind, ViewerScope viewer,
                                 SFunction<T, ?> familyCol, SFunction<T, ?> authorCol, SFunction<T, ?> visibilityCol,
                                 SFunction<T, ?> idCol) {
        Long familyId = viewer.familyId();
        if (familyId == null) {
            // 没有家庭上下文(无演示家庭 / 运维账号):只能看公开内容
            qw.eq(visibilityCol, DictConst.VIS_PUBLIC);
            return;
        }
        boolean shared = idCol != null && canReceiveShare(kind, viewer);
        String audienceSql = shared ? audienceInSql(kind, familyId) : null;
        if (viewer.owner()) {
            if (audienceSql == null) {
                qw.eq(familyCol, familyId); // 家长:本家庭全部
            } else {
                qw.and(w -> w.eq(familyCol, familyId).or().inSql(idCol, audienceSql));
            }
            return;
        }
        if (viewer.guest()) {
            qw.eq(visibilityCol, DictConst.VIS_PUBLIC);
            return;
        }
        qw.and(w -> {
            w.eq(familyCol, familyId)
                    .and(i -> i.eq(authorCol, viewer.userId())
                            .or().in(visibilityCol, memberVisibilities()));
            if (audienceSql != null) w.or().inSql(idCol, audienceSql);
        });
    }

    /** 能否收到别人的分享:必须登录、有家庭,而且不是保险箱(永不进圈) */
    private boolean canReceiveShare(ContentKind kind, ViewerScope viewer) {
        return kind != ContentKind.VAULT && viewer.userId() != null && viewer.familyId() != null;
    }

    /**
     * 「分享给这个家庭」的子查询,供需要自定义组合条件的查询复用(如博客的发布状态分支)。
     * 只拼接枚举 code 与数值 id,无外部字符串。
     */
    public String audienceInSql(ContentKind kind, Long familyId) {
        return "SELECT content_id FROM content_audience WHERE content_type = '" + kind.code()
                + "' AND target_type = 'FAMILY' AND target_id = " + familyId;
    }

    /**
     * 详情/点赞/评论:能否看到这一条。
     * PUBLIC 对所有人可见;同家庭内非 PRIVATE 可见;PRIVATE 仅作者本人与家长;跨家庭仅「被分享」可见。
     */
    public boolean canView(ContentKind kind, Long contentFamilyId, Long authorId, String visibility, ViewerScope viewer) {
        return canView(kind, contentFamilyId, authorId, visibility, viewer, false);
    }

    /**
     * 同上,{@code sharedToViewer}=该条内容是否已分享给观看者所在家庭(content_audience 命中)。
     * PRIVATE 内容永不因分享可见;保险箱永不进圈。
     */
    public boolean canView(ContentKind kind, Long contentFamilyId, Long authorId, String visibility,
                           ViewerScope viewer, boolean sharedToViewer) {
        if (DictConst.VIS_PUBLIC.equals(visibility)) {
            return true;
        }
        if (DictConst.VIS_PRIVATE.equals(visibility)) {
            boolean sameFamily = viewer.familyId() != null && viewer.familyId().equals(contentFamilyId);
            if (!sameFamily || viewer.guest()) return false;
            return viewer.owner() || (viewer.userId() != null && viewer.userId().equals(authorId));
        }
        boolean sameFamily = viewer.familyId() != null && viewer.familyId().equals(contentFamilyId);
        if (sameFamily && !viewer.guest()) {
            return true;
        }
        return sharedToViewer && canReceiveShare(kind, viewer);
    }

    /** 同 {@link #canView},不可见时按项目惯例抛 NOT_FOUND(不泄露存在性) */
    public void assertViewable(ContentKind kind, Long contentFamilyId, Long authorId, String visibility, ViewerScope viewer) {
        if (!canView(kind, contentFamilyId, authorId, visibility, viewer)) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
    }

    /** 同 {@link #assertViewable},带「被分享」标记 */
    public void assertViewable(ContentKind kind, Long contentFamilyId, Long authorId, String visibility,
                               ViewerScope viewer, boolean sharedToViewer) {
        if (!canView(kind, contentFamilyId, authorId, visibility, viewer, sharedToViewer)) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
    }

    /**
     * 能否把这条内容分享出去:策略 OFF 全禁;家长在 OWNER_ONLY 下可分享家庭共有内容;
     * 其余只允许作者本人;PRIVATE 永不进圈。
     */
    public boolean canShare(ContentKind kind, Long contentFamilyId, Long authorId, String visibility,
                            ViewerScope viewer, String sharePolicy) {
        if (kind == ContentKind.VAULT) return false;
        if (DictConst.SHARE_OFF.equals(sharePolicy)) return false;
        if (viewer.userId() == null || viewer.familyId() == null) return false;
        if (!viewer.familyId().equals(contentFamilyId)) return false;
        if (DictConst.VIS_PRIVATE.equals(visibility)) return false;
        if (viewer.userId().equals(authorId)) return true;
        return viewer.owner() && DictConst.SHARE_OWNER_ONLY.equals(sharePolicy);
    }

    /**
     * 供自定义 XML 列表查询使用的可见性参数:判定仍在本类做,XML 只做机械套用
     * (照片等需要 JOIN / RAND() 的查询走这条通道)。
     */
    public VisibleFilter visibleFilter(ViewerScope viewer) {
        Long familyId = viewer.familyId();
        boolean shared = familyId != null && viewer.userId() != null;
        if (familyId == null) {
            return new VisibleFilter(null, null, List.of(DictConst.VIS_PUBLIC), false, false);
        }
        if (viewer.owner()) {
            return new VisibleFilter(familyId, null, List.of(), true, shared);
        }
        if (viewer.guest()) {
            return new VisibleFilter(familyId, null, List.of(DictConst.VIS_PUBLIC), false, false);
        }
        return new VisibleFilter(familyId, viewer.userId(), memberVisibilities(), false, shared);
    }

    /**
     * XML 侧可见性条件参数。
     * {@code all=true} 表示只看家庭维度(家长);{@code shared=true} 表示还要并上「分享给我家」的行。
     */
    public static final class VisibleFilter {

        private final Long familyId;
        private final Long authorId;
        private final List<String> visibilities;
        private final boolean all;
        private final boolean shared;

        public VisibleFilter(Long familyId, Long authorId, List<String> visibilities, boolean all) {
            this(familyId, authorId, visibilities, all, false);
        }

        public VisibleFilter(Long familyId, Long authorId, List<String> visibilities, boolean all, boolean shared) {
            this.familyId = familyId;
            this.authorId = authorId;
            this.visibilities = visibilities;
            this.all = all;
            this.shared = shared;
        }

        public Long getFamilyId() {
            return familyId;
        }

        public Long getAuthorId() {
            return authorId;
        }

        public List<String> getVisibilities() {
            return visibilities;
        }

        public boolean isAll() {
            return all;
        }

        public boolean isShared() {
            return shared;
        }
    }
}
