package com.ihomy.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ihomy.common.BizException;
import com.ihomy.dto.TreeMemberDTO;
import com.ihomy.entity.FamilyTreeMember;
import com.ihomy.entity.SysUser;
import com.ihomy.mapper.FamilyTreeMapper;
import com.ihomy.mapper.SysUserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 家谱服务纯逻辑(Mockito 隔离数据库):
 * 配偶反向补齐、防自引用/成环、家庭成员导入跳过已关联账号、关系专用接口只改关系列。
 */
@ExtendWith(MockitoExtension.class)
class FamilyTreeServiceTest {

    private static final Long FAMILY = 1L;

    @Mock
    private FamilyTreeMapper treeMapper;
    @Mock
    private SysUserMapper sysUserMapper;

    @InjectMocks
    private FamilyTreeService service;

    /** 单测没有 MyBatis-Plus 启动扫描,手动注册实体信息,否则 LambdaWrapper 的列名解析会报错 */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), FamilyTreeMember.class);
    }

    private FamilyTreeMember member(Long id, String name, Long fatherId) {
        FamilyTreeMember m = new FamilyTreeMember();
        m.setId(id);
        m.setFamilyId(FAMILY);
        m.setName(name);
        m.setGeneration(0);
        m.setFatherId(fatherId);
        return m;
    }

    @Test
    void listFillsReverseSpouseSoSingleSidedLinkStillJoins() {
        FamilyTreeMember a = member(1L, "甲", null);
        a.setSpouseId(2L);
        FamilyTreeMember b = member(2L, "乙", null); // 历史数据只写了甲一侧
        when(treeMapper.selectList(any())).thenReturn(List.of(a, b));

        List<Map<String, Object>> rows = service.list(FAMILY);

        assertThat(rows.get(0).get("spouseId")).isEqualTo(2L);
        assertThat(rows.get(1).get("spouseId")).isEqualTo(1L);
        assertThat(rows.get(1).get("spouseName")).isEqualTo("甲");
    }

    @Test
    void updateRejectsBeingItsOwnParentOrSpouse() {
        when(treeMapper.selectById(5L)).thenReturn(member(5L, "甲", null));
        TreeMemberDTO dto = new TreeMemberDTO();
        dto.setName("甲");
        dto.setFatherId(5L);

        assertThatThrownBy(() -> service.update(5L, FAMILY, dto)).isInstanceOf(BizException.class);
        verify(treeMapper, never()).update(any(), any());
    }

    @Test
    void updateRejectsDescendantAsParentToBlockCycle() {
        // 2 的父亲是 1;现在把 2 设为 1 的父亲就成环
        FamilyTreeMember self = member(1L, "甲", null);
        FamilyTreeMember child = member(2L, "乙", 1L);
        when(treeMapper.selectById(1L)).thenReturn(self);
        when(treeMapper.selectById(2L)).thenReturn(child);
        TreeMemberDTO dto = new TreeMemberDTO();
        dto.setName("甲");
        dto.setFatherId(2L);

        assertThatThrownBy(() -> service.update(1L, FAMILY, dto)).isInstanceOf(BizException.class);
        verify(treeMapper, never()).update(any(), any());
    }

    @Test
    void importMembersSkipsAlreadyLinkedAndHonoursSelection() {
        FamilyTreeMember linked = member(10L, "已在家谱", null);
        linked.setUserId(100L);
        when(treeMapper.selectList(any())).thenReturn(List.of(linked));
        when(sysUserMapper.selectList(any())).thenReturn(List.of(
                user(100L, "已在"), user(101L, "待导入"), user(102L, "未选")));

        int count = service.importMembers(FAMILY, List.of(101L));

        assertThat(count).isEqualTo(1);
        ArgumentCaptor<FamilyTreeMember> captor = ArgumentCaptor.forClass(FamilyTreeMember.class);
        verify(treeMapper, times(1)).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(101L);
        assertThat(captor.getValue().getName()).isEqualTo("待导入");
        assertThat(captor.getValue().getFamilyId()).isEqualTo(FAMILY);
    }

    @Test
    void importMembersWithoutSelectionBringsEveryUnlinkedAccount() {
        when(treeMapper.selectList(any())).thenReturn(List.of());
        when(sysUserMapper.selectList(any())).thenReturn(List.of(user(1L, "甲"), user(2L, "乙")));

        assertThat(service.importMembers(FAMILY, null)).isEqualTo(2);
        verify(treeMapper, times(2)).insert(any(FamilyTreeMember.class));
    }

    private SysUser user(Long id, String nickname) {
        SysUser u = new SysUser();
        u.setId(id);
        u.setFamilyId(FAMILY);
        u.setNickname(nickname);
        u.setUsername("u" + id);
        return u;
    }

    // ---------- 关系专用接口(POST/DELETE /tree/relation) ----------

    @SuppressWarnings("unchecked")
    private LambdaUpdateWrapper<FamilyTreeMember> wrapperOf(Wrapper<FamilyTreeMember> w) {
        return (LambdaUpdateWrapper<FamilyTreeMember>) w;
    }

    /** SET 的列名片段(如 father_id=#{...}):用来断言只写了哪几列 */
    private String setOf(Wrapper<FamilyTreeMember> w) {
        return wrapperOf(w).getSqlSet();
    }

    /** wrapper 里的全部绑定值(含 null,null 正是 .set(col, null) 的写法)。
     *  Lambda 条件里的值在 getSqlSegment() 时才落进参数表,先取一次段再取值。 */
    private List<Object> valuesOf(Wrapper<FamilyTreeMember> w) {
        wrapperOf(w).getSqlSegment();
        return new ArrayList<>(wrapperOf(w).getParamNameValuePairs().values());
    }

    @Test
    void linkFatherOnlyWritesTheParentColumnAndKeepsOtherFields() {
        FamilyTreeMember parent = member(1L, "父", null);
        FamilyTreeMember child = member(2L, "子", null);
        when(treeMapper.selectById(1L)).thenReturn(parent);
        when(treeMapper.selectById(2L)).thenReturn(child);

        service.link(FAMILY, 1L, 2L, "FATHER");

        ArgumentCaptor<Wrapper<FamilyTreeMember>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(treeMapper, times(1)).update(any(), captor.capture());
        String set = setOf(captor.getValue());
        assertThat(set).startsWith("father_id=").doesNotContain("gender").doesNotContain("birth_date")
                .doesNotContain("photo").doesNotContain("note").doesNotContain("mother_id").doesNotContain("spouse_id");
        assertThat(valuesOf(captor.getValue())).contains(2L, 1L);
        verify(treeMapper, never()).updateById(any(FamilyTreeMember.class));
    }

    @Test
    void linkRejectsSelfReference() {
        assertThatThrownBy(() -> service.link(FAMILY, 3L, 3L, "FATHER")).isInstanceOf(BizException.class);
        verify(treeMapper, never()).update(any(), any());
    }

    @Test
    void linkRejectsUnknownKind() {
        when(treeMapper.selectById(1L)).thenReturn(member(1L, "甲", null));
        when(treeMapper.selectById(2L)).thenReturn(member(2L, "乙", null));

        assertThatThrownBy(() -> service.link(FAMILY, 1L, 2L, "UNCLE")).isInstanceOf(BizException.class);
        verify(treeMapper, never()).update(any(), any());
    }

    @Test
    void linkRejectsMemberFromAnotherFamily() {
        FamilyTreeMember other = member(2L, "乙", null);
        other.setFamilyId(99L);
        when(treeMapper.selectById(1L)).thenReturn(member(1L, "甲", null));
        when(treeMapper.selectById(2L)).thenReturn(other);

        assertThatThrownBy(() -> service.link(FAMILY, 1L, 2L, "FATHER")).isInstanceOf(BizException.class);
        verify(treeMapper, never()).update(any(), any());
    }

    @Test
    void linkRejectsMakingDescendantAParent() {
        // 2 的父亲是 1;再把 2 设成 1 的父亲就成环(3 是 2 的父亲)
        FamilyTreeMember descendant = member(4L, "孙", null);
        descendant.setFatherId(1L);
        when(treeMapper.selectById(4L)).thenReturn(descendant);
        when(treeMapper.selectById(1L)).thenReturn(member(1L, "祖", null));

        assertThatThrownBy(() -> service.link(FAMILY, 4L, 1L, "FATHER")).isInstanceOf(BizException.class);
        verify(treeMapper, never()).update(any(), any());
    }

    @Test
    void linkSpouseClearsOldPartnersAndBindsBothSides() {
        FamilyTreeMember a = member(3L, "甲", null);
        a.setSpouseId(5L);
        FamilyTreeMember b = member(4L, "乙", null);
        when(treeMapper.selectById(3L)).thenReturn(a);
        when(treeMapper.selectById(4L)).thenReturn(b);
        when(treeMapper.selectById(5L)).thenReturn(member(5L, "旧配偶", null));

        service.link(FAMILY, 3L, 4L, "SPOUSE");

        ArgumentCaptor<Wrapper<FamilyTreeMember>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(treeMapper, times(3)).update(any(), captor.capture());
        // 第一次清旧配偶 5 的反向引用,后两次把 3 与 4 互相指向
        String first = setOf(captor.getAllValues().get(0));
        assertThat(first).startsWith("spouse_id=");
        assertThat(valuesOf(captor.getAllValues().get(0))).contains(5L).containsNull();
        assertThat(setOf(captor.getAllValues().get(1))).startsWith("spouse_id=");
        assertThat(valuesOf(captor.getAllValues().get(1))).contains(3L, 4L);
        assertThat(setOf(captor.getAllValues().get(2))).startsWith("spouse_id=");
        assertThat(valuesOf(captor.getAllValues().get(2))).contains(4L, 3L);
    }

    @Test
    void unlinkFatherClearsOnlyThatSideWhenItPointsToTheMember() {
        when(treeMapper.selectById(1L)).thenReturn(member(1L, "父", null));
        when(treeMapper.selectById(2L)).thenReturn(member(2L, "子", null));

        service.unlink(FAMILY, 1L, 2L, "FATHER");

        ArgumentCaptor<Wrapper<FamilyTreeMember>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(treeMapper, times(1)).update(any(), captor.capture());
        assertThat(setOf(captor.getValue())).startsWith("father_id=").doesNotContain("mother_id");
        assertThat(valuesOf(captor.getValue())).contains(2L, 1L).containsNull();
    }

    @Test
    void unlinkSpouseClearsBothMutualSides() {
        FamilyTreeMember a = member(3L, "甲", null);
        a.setSpouseId(4L);
        FamilyTreeMember b = member(4L, "乙", null);
        b.setSpouseId(3L);
        when(treeMapper.selectById(3L)).thenReturn(a);
        when(treeMapper.selectById(4L)).thenReturn(b);

        service.unlink(FAMILY, 3L, 4L, "SPOUSE");

        ArgumentCaptor<Wrapper<FamilyTreeMember>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(treeMapper, times(2)).update(any(), captor.capture());
        assertThat(captor.getAllValues()).allSatisfy((w) -> {
            assertThat(setOf(w)).startsWith("spouse_id=");
            assertThat(valuesOf(w)).contains((Object) null);
        });
    }
}
