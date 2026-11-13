package com.ihomy.service.access;

import com.ihomy.common.BizException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 可见性判定单点(阶段 A):canView 的主体×可见档位矩阵,以及给 XML 用的 visibleFilter。
 * 纯逻辑单测,不起 Spring/DB。
 */
class ContentAccessServiceTest {

    private final ContentAccessService service = new ContentAccessService();

    private static final Long FAMILY_A = 10L;
    private static final Long FAMILY_B = 20L;
    private static final Long AUTHOR = 100L;
    private static final Long MEMBER = 101L;

    private static ViewerScope memberOfA() {
        return ViewerScope.of(MEMBER, FAMILY_A, false);
    }

    private static ViewerScope ownerOfA() {
        return ViewerScope.of(200L, FAMILY_A, true);
    }

    private static ViewerScope guestOnA() {
        return ViewerScope.guest(FAMILY_A);
    }

    private static ViewerScope memberOfB() {
        return ViewerScope.of(300L, FAMILY_B, false);
    }

    // ---------- canView:同家庭 ----------

    @Test
    void familyVisible_isVisibleToSameFamilyMembers() {
        assertThat(service.canView(ContentKind.PHOTO, FAMILY_A, AUTHOR, "FAMILY", memberOfA())).isTrue();
        assertThat(service.canView(ContentKind.PHOTO, FAMILY_A, AUTHOR, "FAMILY", ownerOfA())).isTrue();
    }

    @Test
    void private_isVisibleToAuthorOnly() {
        assertThat(service.canView(ContentKind.DIARY, FAMILY_A, AUTHOR, "PRIVATE",
                ViewerScope.of(AUTHOR, FAMILY_A, false))).isTrue();
        // 家长可代管家庭内容
        assertThat(service.canView(ContentKind.DIARY, FAMILY_A, AUTHOR, "PRIVATE", ownerOfA())).isTrue();
        // 同家庭的其他成员看不到别人「仅自己可见」的内容
        assertThat(service.canView(ContentKind.DIARY, FAMILY_A, AUTHOR, "PRIVATE", memberOfA())).isFalse();
    }

    @Test
    void public_isVisibleToEveryone() {
        assertThat(service.canView(ContentKind.BLOG, FAMILY_A, AUTHOR, "PUBLIC", guestOnA())).isTrue();
        assertThat(service.canView(ContentKind.BLOG, FAMILY_A, AUTHOR, "PUBLIC", memberOfB())).isTrue();
        assertThat(service.canView(ContentKind.BLOG, FAMILY_A, AUTHOR, "PUBLIC", memberOfA())).isTrue();
    }

    // ---------- canView:跨家庭 / 游客 ----------

    @Test
    void familyVisible_isInvisibleAcrossFamilies() {
        assertThat(service.canView(ContentKind.PHOTO, FAMILY_A, AUTHOR, "FAMILY", memberOfB())).isFalse();
        assertThat(service.canView(ContentKind.PHOTO, FAMILY_A, AUTHOR, "FAMILY", guestOnA())).isFalse();
    }

    @Test
    void private_isInvisibleAcrossFamiliesEvenToAuthorAfterSwitch() {
        // 作者切到别的家庭后,旧家庭的私密内容不再可见(与原口径一致)
        assertThat(service.canView(ContentKind.DIARY, FAMILY_A, AUTHOR, "PRIVATE", memberOfB())).isFalse();
    }

    @Test
    void unknownVisibility_treatedAsFamilyScoped() {
        assertThat(service.canView(ContentKind.BOOK, FAMILY_A, AUTHOR, null, memberOfA())).isTrue();
        assertThat(service.canView(ContentKind.BOOK, FAMILY_A, AUTHOR, null, memberOfB())).isFalse();
    }

    @Test
    void assertViewable_throwsNotFoundWhenHidden() {
        assertThatThrownBy(() -> service.assertViewable(ContentKind.PHOTO, FAMILY_A, AUTHOR, "FAMILY", guestOnA()))
                .isInstanceOf(BizException.class);
        service.assertViewable(ContentKind.PHOTO, FAMILY_A, AUTHOR, "FAMILY", memberOfA());
    }

    // ---------- visibleFilter(XML 列表参数) ----------

    @Test
    void visibleFilter_ownerSeesWholeFamily() {
        ContentAccessService.VisibleFilter f = service.visibleFilter(ownerOfA());
        assertThat(f.isAll()).isTrue();
        assertThat(f.getFamilyId()).isEqualTo(FAMILY_A);
    }

    @Test
    void visibleFilter_memberSeesOwnOrFamilyWide() {
        ContentAccessService.VisibleFilter f = service.visibleFilter(memberOfA());
        assertThat(f.isAll()).isFalse();
        assertThat(f.getFamilyId()).isEqualTo(FAMILY_A);
        assertThat(f.getAuthorId()).isEqualTo(MEMBER);
        assertThat(f.getVisibilities()).containsExactly("FAMILY", "PUBLIC");
    }

    @Test
    void visibleFilter_guestSeesPublicOnlyInDemoFamily() {
        ContentAccessService.VisibleFilter f = service.visibleFilter(guestOnA());
        assertThat(f.isAll()).isFalse();
        assertThat(f.getFamilyId()).isEqualTo(FAMILY_A);
        assertThat(f.getAuthorId()).isNull();
        assertThat(f.getVisibilities()).containsExactly("PUBLIC");
    }

    @Test
    void visibleFilter_withoutFamilyFallsBackToPublic() {
        ContentAccessService.VisibleFilter f = service.visibleFilter(ViewerScope.guest(null));
        assertThat(f.getFamilyId()).isNull();
        assertThat(f.getVisibilities()).containsExactly("PUBLIC");
    }

    @Test
    void memberVisibilities_excludesPrivate() {
        assertThat(service.memberVisibilities()).containsExactly("FAMILY", "PUBLIC");
    }

    // ---------- 阶段 B:「被分享」与分享入口 ----------

    @Test
    void sharedContent_isVisibleToTargetFamilyMembersOnly() {
        assertThat(service.canView(ContentKind.PHOTO, FAMILY_A, AUTHOR, "FAMILY", memberOfB(), true)).isTrue();
        assertThat(service.canView(ContentKind.PHOTO, FAMILY_A, AUTHOR, "FAMILY", memberOfB(), false)).isFalse();
        // 第三个家庭没有受众行,仍然看不到
        assertThat(service.canView(ContentKind.PHOTO, FAMILY_A, AUTHOR, "FAMILY",
                ViewerScope.of(400L, 30L, false), false)).isFalse();
    }

    @Test
    void sharedPrivateContent_staysInvisible() {
        assertThat(service.canView(ContentKind.PHOTO, FAMILY_A, AUTHOR, "PRIVATE", memberOfB(), true)).isFalse();
        assertThat(service.canView(ContentKind.PHOTO, FAMILY_A, AUTHOR, "PRIVATE", ownerOfA(), true)).isTrue();
    }

    @Test
    void sharedContent_isInvisibleToGuests() {
        // 游客没有登录主体,即便演示家庭被分享过也不能看
        assertThat(service.canView(ContentKind.PHOTO, FAMILY_A, AUTHOR, "FAMILY", guestOnA(), true)).isFalse();
    }

    @Test
    void visibleFilter_marksSharedOnlyForLoggedInUsers() {
        assertThat(service.visibleFilter(memberOfA()).isShared()).isTrue();
        assertThat(service.visibleFilter(ownerOfA()).isShared()).isTrue();
        assertThat(service.visibleFilter(guestOnA()).isShared()).isFalse();
        assertThat(service.visibleFilter(ViewerScope.publicView(FAMILY_A)).isShared()).isFalse();
    }

    @Test
    void canShare_onlyAuthorOrOwnerUnderOwnerOnlyPolicy() {
        // 作者本人:任何非 OFF、非 PRIVATE 都能分享
        assertThat(service.canShare(ContentKind.PHOTO, FAMILY_A, MEMBER, "FAMILY", memberOfA(), "SELF_ONLY")).isTrue();
        // 同家庭其他成员:默认策略下不能分享别人的内容
        assertThat(service.canShare(ContentKind.PHOTO, FAMILY_A, AUTHOR, "FAMILY", memberOfA(), "SELF_ONLY")).isFalse();
        // 家长 + OWNER_ONLY:可以分享家庭共有内容
        assertThat(service.canShare(ContentKind.PHOTO, FAMILY_A, AUTHOR, "FAMILY", ownerOfA(), "OWNER_ONLY")).isTrue();
        // 关闭外分享:谁都不行
        assertThat(service.canShare(ContentKind.PHOTO, FAMILY_A, MEMBER, "FAMILY", memberOfA(), "OFF")).isFalse();
        // 私密内容与保险箱永不进圈
        assertThat(service.canShare(ContentKind.PHOTO, FAMILY_A, MEMBER, "PRIVATE", memberOfA(), "SELF_ONLY")).isFalse();
        assertThat(service.canShare(ContentKind.VAULT, FAMILY_A, MEMBER, "FAMILY", memberOfA(), "SELF_ONLY")).isFalse();
        // 跨家庭:不是自己的内容不能分享
        assertThat(service.canShare(ContentKind.PHOTO, FAMILY_A, AUTHOR, "FAMILY", memberOfB(), "SELF_ONLY")).isFalse();
    }
}
