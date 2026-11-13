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
        Long familyId = viewer.familyId();
        if (familyId == null) {
            // 没有家庭上下文(无演示家庭 / 运维账号):只能看公开内容
            qw.eq(visibilityCol, DictConst.VIS_PUBLIC);
            return;
        }
        qw.eq(familyCol, familyId);
        if (viewer.owner()) {
            return; // 家长:本家庭全部
        }
        if (viewer.guest()) {
            qw.eq(visibilityCol, DictConst.VIS_PUBLIC);
            return;
        }
        qw.and(w -> w.eq(authorCol, viewer.userId())
                .or().in(visibilityCol, memberVisibilities()));
    }

    /**
     * 详情/点赞/评论:能否看到这一条。
     * PUBLIC 对所有人可见;同家庭内非 PRIVATE 可见;PRIVATE 仅作者本人与家长;跨家庭一律不可见。
     */
    public boolean canView(ContentKind kind, Long contentFamilyId, Long authorId, String visibility, ViewerScope viewer) {
        if (DictConst.VIS_PUBLIC.equals(visibility)) {
            return true;
        }
        boolean sameFamily = viewer.familyId() != null && viewer.familyId().equals(contentFamilyId);
        if (!sameFamily || viewer.guest()) {
            return false;
        }
        if (DictConst.VIS_PRIVATE.equals(visibility)) {
            return viewer.owner() || (viewer.userId() != null && viewer.userId().equals(authorId));
        }
        return true;
    }

    /** 同 {@link #canView},不可见时按项目惯例抛 NOT_FOUND(不泄露存在性) */
    public void assertViewable(ContentKind kind, Long contentFamilyId, Long authorId, String visibility, ViewerScope viewer) {
        if (!canView(kind, contentFamilyId, authorId, visibility, viewer)) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
    }

    /**
     * 供自定义 XML 列表查询使用的可见性参数:判定仍在本类做,XML 只做机械套用
     * (照片等需要 JOIN / RAND() 的查询走这条通道)。
     */
    public VisibleFilter visibleFilter(ViewerScope viewer) {
        Long familyId = viewer.familyId();
        if (familyId == null) {
            return new VisibleFilter(null, null, List.of(DictConst.VIS_PUBLIC), false);
        }
        if (viewer.owner()) {
            return new VisibleFilter(familyId, null, List.of(), true);
        }
        if (viewer.guest()) {
            return new VisibleFilter(familyId, null, List.of(DictConst.VIS_PUBLIC), false);
        }
        return new VisibleFilter(familyId, viewer.userId(), memberVisibilities(), false);
    }

    /**
     * XML 侧可见性条件参数。
     * {@code all=true} 表示只看家庭维度(家长);否则家庭 + (authorId 本人 OR visibilities 档位)。
     */
    public static final class VisibleFilter {

        private final Long familyId;
        private final Long authorId;
        private final List<String> visibilities;
        private final boolean all;

        public VisibleFilter(Long familyId, Long authorId, List<String> visibilities, boolean all) {
            this.familyId = familyId;
            this.authorId = authorId;
            this.visibilities = visibilities;
            this.all = all;
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
    }
}
