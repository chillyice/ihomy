package com.ihomy.service;

import com.ihomy.common.BizException;
import com.ihomy.dto.TreeMemberDTO;
import com.ihomy.entity.FamilyTreeMember;
import com.ihomy.entity.SysUser;
import com.ihomy.mapper.FamilyTreeMapper;
import com.ihomy.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
 * 配偶反向补齐、防自引用/成环、家庭成员导入跳过已关联账号。
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
}
