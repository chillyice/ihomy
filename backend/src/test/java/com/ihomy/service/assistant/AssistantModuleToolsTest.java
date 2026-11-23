package com.ihomy.service.assistant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ihomy.common.BizException;
import com.ihomy.mapper.SysRoleMapper;
import com.ihomy.security.LoginUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AssistantModuleToolsTest {
    private final SecurityHelper security = mock(SecurityHelper.class);
    private final SysRoleMapper roles = mock(SysRoleMapper.class);
    private final DiaryService diaries = mock(DiaryService.class);
    private final BookService book = mock(BookService.class);
    private final BookPermissionService bookAccess = mock(BookPermissionService.class);
    private final BookAccountService accounts = mock(BookAccountService.class);
    private final FamilyPlanService plans = mock(FamilyPlanService.class);
    private final TaskService tasks = mock(TaskService.class);
    private final AnniversaryService anniversaries = mock(AnniversaryService.class);
    private final RecipeService recipes = mock(RecipeService.class);
    private final LibraryService library = mock(LibraryService.class);
    private final HomeAssistantService iot = mock(HomeAssistantService.class);
    private final PointsService points = mock(PointsService.class);
    private final HealthQueryService health = mock(HealthQueryService.class);
    private final ItemLocalParser itemParser = mock(ItemLocalParser.class);
    private final ItemService items = mock(ItemService.class);

    private final AssistantReadTools reads = new AssistantReadTools(plans, anniversaries, tasks, book, bookAccess, accounts,
            library, null, null, null, recipes, iot, points, null, health);
    private final AssistantWrites writes = new AssistantWrites(diaries, null, null, null, book, bookAccess, null, null,
            null, itemParser, items, plans, tasks, anniversaries, recipes, library, iot, points, security);
    private final AssistantTools tools = new AssistantTools(security, roles, null, null, null, reads, writes);

    @BeforeEach
    void loggedIn() {
        when(security.current()).thenReturn(new LoginUser(7L, "user", "MEMBER", 3L));
        when(roles.selectRoleCodeByUserAndFamily(7L, 3L)).thenReturn("MEMBER");
    }

    @Test
    void catalogCoversModulesAndMarksWrites() {
        List<String> names = tools.catalog().stream().map(t -> (String) t.get("name")).toList();
        assertTrue(names.containsAll(List.of("get_plans", "list_anniversaries", "list_tasks", "get_book_summary",
                "list_book_records", "list_library", "list_videos", "list_music", "list_announcements", "search_recipes",
                "list_iot_devices", "get_health_summary", "get_points", "list_family_tree", "create_plan", "add_plan_task",
                "create_task", "create_anniversary", "create_recipe", "task_action", "update_reading_status", "iot_control",
                "redeem_points", "check_in", "take_item")));
        assertTrue(tools.isWrite("iot_control"));
        assertFalse(tools.isWrite("get_plans"));
    }

    @Test
    void bookReadsEnforcePermissionAndHealthReadsOnlySelf() {
        when(book.summary(3L)).thenReturn(Map.of("month", "2026-11", "income", 10));
        when(accounts.list(3L)).thenReturn(List.of());
        assertEquals("2026-11", tools.read("get_book_summary", Map.of()).get("month"));
        verify(bookAccess).requireRead(any());

        when(health.summary(7L, 7)).thenReturn(Map.of("hasData", false));
        assertEquals(false, tools.read("get_health_summary", Map.of()).get("hasData"));
        verify(health).summary(7L, 7);
    }

    @Test
    void planWriteRequiresDateForReminderAndResolvesExistingPlan() {
        assertThrows(BizException.class, () -> tools.prepare("create_plan", Map.of("title", "大扫除", "remindTime", "09:00")));
        var plan = tools.prepare("create_plan", Map.of("title", "大扫除", "targetDate", "2026-11-30", "remindTime", "09:00"));
        assertTrue(tools.preview("create_plan", plan).contains("2026-11-30"));

        when(plans.list(3L)).thenReturn(List.of(Map.of("id", 11L, "title", "搬家", "status", "ACTIVE")));
        var task = tools.prepare("add_plan_task", Map.of("plan", "搬家", "title", "打包书籍"));
        assertEquals(11L, ((Number) task.get("planId")).longValue());
        assertTrue(tools.preview("add_plan_task", task).contains("打包书籍"));
    }

    @Test
    void anniversaryAndTaskWritesValidateInputsBeforeShowingCard() {
        assertThrows(BizException.class, () -> tools.prepare("create_anniversary", Map.of("name", "生日", "month", 2, "day", 30)));
        assertThrows(BizException.class, () -> tools.prepare("create_anniversary",
                Map.of("name", "生日", "month", 7, "day", 31, "calendar", "lunar")));
        var ok = tools.prepare("create_anniversary", Map.of("name", "生日", "month", 11, "day", 23));
        assertTrue(tools.preview("create_anniversary", ok).contains("每年"));

        assertThrows(BizException.class, () -> tools.prepare("create_task", Map.of("title", "洗碗", "rewardType", "POINTS")));
        var task = tools.prepare("create_task", Map.of("title", "洗碗", "rewardType", "POINTS", "rewardPoints", 10));
        assertTrue(tools.preview("create_task", task).contains("10 积分"));
    }

    @Test
    void iotControlRequiresPermissionAndTakeItemRejectsShortStock() {
        when(security.hasPermission("iot:control")).thenReturn(false);
        assertThrows(BizException.class, () -> tools.prepare("iot_control", Map.of("device", "客厅灯", "action", "ON")));

        when(itemParser.findLocal(any(), anyString())).thenReturn(List.of(
                Map.of("id", 5L, "name", "牛奶", "quantity", 1, "unit", "瓶", "family_id", 3L)));
        assertThrows(BizException.class, () -> tools.prepare("take_item", Map.of("name", "牛奶", "amount", 2)));
        var saved = tools.prepare("take_item", Map.of("name", "牛奶", "amount", 1));
        assertEquals(5L, ((Number) saved.get("itemId")).longValue());
    }

    @Test
    void checkedInUserCannotPrepareAnotherCheckIn() {
        when(points.stats(7L, 3L)).thenReturn(Map.of("checkedToday", true));
        assertThrows(BizException.class, () -> tools.prepare("check_in", Map.of()));
        verifyNoInteractions(items);
    }
}
