package com.ihomy.service.assistant;

import com.ihomy.common.BizException;
import com.ihomy.dto.DiaryDTO;
import com.ihomy.entity.Diary;
import com.ihomy.mapper.SysRoleMapper;
import com.ihomy.security.LoginUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.DiaryService;
import com.ihomy.service.ItemAiService;
import com.ihomy.service.access.ViewerScope;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssistantToolsTest {
    private final SecurityHelper security = mock(SecurityHelper.class);
    private final SysRoleMapper roles = mock(SysRoleMapper.class);
    private final DiaryService diaries = mock(DiaryService.class);
    private final ItemAiService itemAi = mock(ItemAiService.class);
    private final AssistantWrites writes = new AssistantWrites(diaries, null, null, null, null, null, null, null, null, null, null);
    private final AssistantTools tools = new AssistantTools(security, roles, null, null, itemAi, writes);

    @BeforeEach
    void loggedIn() {
        when(security.current()).thenReturn(new LoginUser(7L, "user", "OWNER", 3L));
        when(roles.selectRoleCodeByUserAndFamily(7L, 3L)).thenReturn("CHILD");
    }

    @Test
    void diaryVisibilityIsSelectedAtConfirmationAndOwnedByCurrentUser() {
        var saved = tools.prepare("create_diary", Map.of("content", "记下今天", "date", "2026-11-20"));
        assertEquals("PRIVATE", saved.get("visibility"));
        verifyNoInteractions(diaries);
        assertThrows(BizException.class, () -> tools.prepare("create_diary", Map.of("content", "今天", "visibility", "FAMILY")));
        Diary entry = new Diary();
        entry.setId(8L);
        when(diaries.create(eq(7L), eq(3L), any())).thenReturn(entry);
        saved.put("visibility", "FAMILY");
        assertEquals("/diary/book/7?entry=8", tools.execute("create_diary", saved).get("path"));
        ArgumentCaptor<DiaryDTO> form = ArgumentCaptor.forClass(DiaryDTO.class);
        verify(diaries).create(eq(7L), eq(3L), form.capture());
        assertEquals("FAMILY", form.getValue().getVisibility());
        saved.put("visibility", "PUBLIC");
        assertThrows(BizException.class, () -> tools.execute("create_diary", saved));
    }

    @Test
    void fullDiaryBodyIsVisibleBeforeConfirmationEvenAtTheSizeLimit() {
        String content = "日".repeat(7999) + "终";
        var saved = tools.prepare("create_diary", Map.of("content", content));
        assertTrue(tools.preview("create_diary", saved).endsWith("\n" + content));
        assertThrows(BizException.class, () -> tools.prepare("create_diary", Map.of("content", content + "!")));
        verifyNoInteractions(diaries);
    }

    @Test
    void rechecksLiveRoleDuringConfirmationAndRejectsVisitors() {
        var args = tools.prepare("create_diary", Map.of("content", "记下今天"));
        when(roles.selectRoleCodeByUserAndFamily(7L, 3L)).thenReturn("GUEST");
        assertThrows(BizException.class, () -> tools.execute("create_diary", args));
        assertThrows(BizException.class, () -> tools.prepare("create_wish", Map.of("title", "愿望")));
        when(roles.selectRoleCodeByUserAndFamily(7L, 3L)).thenReturn(null);
        assertThrows(BizException.class, () -> tools.read("open_page", Map.of("page", "blog")));
        verifyNoInteractions(diaries);
    }

    @Test
    void toolsRejectInjectedIdentityUnknownDestinationsAndInvalidParameterTypes() {
        assertThrows(BizException.class, () -> tools.read("open_page", Map.of("page", "/ops")));
        assertThrows(BizException.class, () -> tools.read("get_schedule", Map.of("days", 1.5)));
        assertThrows(BizException.class, () -> tools.read("get_schedule", Map.of("days", 32)));
        assertThrows(BizException.class, () -> tools.prepare("notify_family", Map.of("familyId", 1, "memberId", 2, "message", "你好")));
        assertThrows(BizException.class, () -> tools.prepare("create_reminder", Map.of("title", "test", "date", "2026-02-30", "time", "09:00")));
        assertThrows(BizException.class, () -> tools.read("create_wish", Map.of("title", "wish")));
        assertThrows(BizException.class, () -> tools.prepare("open_page", Map.of("page", "blog")));
        assertEquals("/diary", tools.read("open_page", Map.of("page", "diary")).get("path"));
    }

    @Test
    void findDisclosesOnlyLocationFieldsAndOwnFamilyResults() {
        ViewerScope viewer = ViewerScope.of(7L, 3L, false);
        when(itemAi.findWithinFamily(viewer, "钥匙")).thenReturn(Map.of("matches", List.of(
                Map.of("family_id", 3L, "name", "钥匙", "room_name", "客厅", "note", "personal"),
                Map.of("family_id", 9L, "name", "不应返回"))));
        assertEquals(List.of(Map.of("name", "钥匙", "room_name", "客厅")), tools.read("find_item", Map.of("query", "钥匙")).get("matches"));
    }
}
