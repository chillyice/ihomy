package com.ihomy.service;

import com.ihomy.common.BizException;
import com.ihomy.entity.ChatRead;
import com.ihomy.entity.SysUserRole;
import com.ihomy.mapper.ChatDmMessageMapper;
import com.ihomy.mapper.ChatMessageMapper;
import com.ihomy.mapper.ChatReadMapper;
import com.ihomy.mapper.FamilyMapper;
import com.ihomy.mapper.SysUserMapper;
import com.ihomy.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 聊天室房间准入与房间列表(Mockito 隔离数据库):
 * 本家直接放行、活跃亲友圈家庭放行、不连通/未绑定家庭一律 404;房间列表的未读数按最新 id − 已读游标。
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    private static final Long USER = 7L;
    private static final Long FAMILY = 1L;
    private static final Long PEER_FAMILY = 2L;

    @Mock
    private ChatMessageMapper messageMapper;
    @Mock
    private ChatDmMessageMapper dmMapper;
    @Mock
    private ChatReadMapper readMapper;
    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private SysUserRoleMapper sysUserRoleMapper;
    @Mock
    private FamilyMapper familyMapper;
    @Mock
    private CircleService circleService;

    @InjectMocks
    private ChatService service;

    private void boundTo(Long userId, Long... familyIds) {
        List<SysUserRole> rows = java.util.Arrays.stream(familyIds).map(f -> roleRow(userId, f)).toList();
        when(sysUserRoleMapper.selectList(any())).thenReturn(rows);
    }

    private SysUserRole roleRow(Long userId, Long familyId) {
        SysUserRole r = new SysUserRole();
        r.setUserId(userId);
        r.setFamilyId(familyId);
        return r;
    }

    /**
     * 通话准入(WS call-signal 前置):按「共享任一家庭绑定」判定,不看主家庭。
     * 场景:我在养生协会(第二家庭),小明主家庭就是养生协会——V12.74 之前按 sys_user.family_id 比对会拒,
     * 表现为主叫一直等到超时,被叫完全不响铃。
     */
    @Test
    void callAllowedWhenUsersShareAnyFamilyBinding() {
        when(sysUserRoleMapper.selectList(any()))
                .thenReturn(List.of(roleRow(USER, 2L), roleRow(USER, 41L)))
                .thenReturn(List.of(roleRow(9L, 41L)));

        assertThat(service.sameFamily(USER, 9L)).isTrue();
    }

    @Test
    void callRejectedWhenUsersShareNoFamily() {
        when(sysUserRoleMapper.selectList(any()))
                .thenReturn(List.of(roleRow(USER, 2L)))
                .thenReturn(List.of(roleRow(9L, 41L)));

        assertThat(service.sameFamily(USER, 9L)).isFalse();
    }

    @Test
    void ownFamilyRoomAlwaysAllowed() {
        boundTo(USER, FAMILY);
        assertThat(service.requireRoomAccess(USER, FAMILY, null)).isEqualTo(FAMILY);
        assertThat(service.requireRoomAccess(USER, FAMILY, FAMILY)).isEqualTo(FAMILY);
    }

    @Test
    void circleSiblingRoomAllowed() {
        boundTo(USER, FAMILY);
        when(circleService.connectedFamilyIds(FAMILY)).thenReturn(Set.of(FAMILY, PEER_FAMILY));
        assertThat(service.requireRoomAccess(USER, FAMILY, PEER_FAMILY)).isEqualTo(PEER_FAMILY);
    }

    @Test
    void unrelatedFamilyRoomRejected() {
        boundTo(USER, FAMILY);
        when(circleService.connectedFamilyIds(FAMILY)).thenReturn(Set.of(FAMILY));
        assertThatThrownBy(() -> service.requireRoomAccess(USER, FAMILY, PEER_FAMILY))
                .isInstanceOf(BizException.class);
    }

    @Test
    void userNotBoundToCurrentFamilyRejected() {
        boundTo(USER, 99L);
        assertThatThrownBy(() -> service.requireRoomAccess(USER, FAMILY, null))
                .isInstanceOf(BizException.class);
    }

    @Test
    void canAccessRoomFollowsCircleConnectivity() {
        when(circleService.connectedFamilyIds(FAMILY)).thenReturn(Set.of(FAMILY, PEER_FAMILY));
        when(circleService.connectedFamilyIds(3L)).thenReturn(Set.of(3L));
        assertThat(service.canAccessRoom(FAMILY, FAMILY)).isTrue();
        assertThat(service.canAccessRoom(FAMILY, PEER_FAMILY)).isTrue();
        assertThat(service.canAccessRoom(3L, PEER_FAMILY)).isFalse();
        assertThat(service.canAccessRoom(null, PEER_FAMILY)).isFalse();
    }

    @Test
    void roomsComputeUnreadFromLastAndCursor() {
        Map<String, Object> own = new LinkedHashMap<>();
        own.put("familyId", FAMILY);
        own.put("familyName", "本家");
        own.put("self", true);
        Map<String, Object> peer = new LinkedHashMap<>();
        peer.put("familyId", PEER_FAMILY);
        peer.put("familyName", "爸妈家");
        peer.put("alias", "爸妈");
        peer.put("self", false);
        when(circleService.connectedFamilies(FAMILY)).thenReturn(List.of(own, peer));

        when(messageMapper.selectLastIds(List.of(FAMILY, PEER_FAMILY))).thenReturn(List.of(
                Map.of("familyId", FAMILY, "lastMsgId", 120L),
                Map.of("familyId", PEER_FAMILY, "lastMsgId", 8L)));

        ChatRead read = new ChatRead();
        read.setUserId(USER);
        read.setFamilyId(FAMILY);
        read.setLastReadMsgId(100L);
        when(readMapper.selectList(any())).thenReturn(List.of(read));

        List<Map<String, Object>> rooms = service.rooms(USER, FAMILY);
        assertThat(rooms).hasSize(2);
        assertThat(rooms.get(0).get("unread")).isEqualTo(20L);
        assertThat(rooms.get(1).get("unread")).isEqualTo(8L);
        assertThat(rooms.get(1).get("alias")).isEqualTo("爸妈");
    }
}
