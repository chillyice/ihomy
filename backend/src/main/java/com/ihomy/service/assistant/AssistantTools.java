package com.ihomy.service.assistant;

import static com.ihomy.service.assistant.AssistantToolSpec.*;

import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import com.ihomy.mapper.SysRoleMapper;
import com.ihomy.security.LoginUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.ItemAiService;
import com.ihomy.service.ScheduleService;
import com.ihomy.service.access.ViewerScope;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Callers only supply tool arguments. Identity, family and current role always come from the server. */
@Service
@RequiredArgsConstructor
public class AssistantTools {
    static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    // Explicit subset of NAV_PATHS. No arbitrary path, query, privileged or sensitive destination.
    private static final Map<String, String> PAGES = Map.ofEntries(
            Map.entry("album", "/album"), Map.entry("anniversary", "/anniversary"),
            Map.entry("announcement", "/announcement"), Map.entry("blog", "/blog"), Map.entry("book", "/book"),
            Map.entry("chat", "/chat"), Map.entry("cinema", "/cinema"), Map.entry("diary", "/diary"),
            Map.entry("games", "/games"), Map.entry("help", "/help"), Map.entry("item", "/item"),
            Map.entry("kitchen", "/kitchen"), Map.entry("library", "/library"), Map.entry("member", "/member"),
            Map.entry("music", "/music"), Map.entry("plan", "/plan"), Map.entry("plant", "/plant"),
            Map.entry("points", "/points"), Map.entry("reminder", "/reminder"), Map.entry("settings", "/settings"),
            Map.entry("storage", "/storage/files"), Map.entry("task", "/task"), Map.entry("tools", "/tools"),
            Map.entry("tree", "/tree"), Map.entry("wish", "/wish"));
    private static final List<AssistantToolSpec> SPECS = List.of(
            spec("get_schedule", "查本人家庭日程。from 是起始公历日期 YYYY-MM-DD，缺省今天；days 为天数，缺省 7。查今天传 1。", false,
                    text("from", 10, false), integer("days", 31, false)),
            spec("find_item", "按用户原话找物，仅从本人可见的物品中返回名称和存放位置。", false, text("query", 300, true)),
            spec("list_family_members", "列出本家庭成员的姓名、角色和编号；通知前用它定位收件人。", false),
            spec("search_content", "在本家庭查可见内容。一般须提供 keyword；找自己的日记用 kind=diary 和 mine=true，可按 from/to 限定日期。", false,
                    choice("kind", false, "blog", "diary", "album", "wish", "item", "member"),
                    text("keyword", 100, false), integer("limit", 10, false), text("from", 10, false),
                    text("to", 10, false), flag("mine")),
            spec("open_page", "给用户一个站内功能按钮，由用户点击打开。只接受模块名。", false,
                    choice("page", true, PAGES.keySet().stream().sorted().toArray(String[]::new))),
            spec("create_reminder", "新建家庭提醒。与用户明确日期和时刻，再给出公历 YYYY-MM-DD 和 HH:mm。缺少信息先询问。", true,
                    text("title", 100, true), text("date", 10, true), text("time", 5, true),
                    choice("repeat", false, "ONCE", "DAILY", "WEEKLY", "MONTHLY", "YEARLY")),
            spec("create_diary", "起草本人的日记，确认卡默认仅自己可见，用户可自行改为本家庭可见。", true,
                    text("content", 8000, true), text("date", 10, false)),
            spec("create_blog", "起草博客，确认后存为家庭可见范围的草稿。", true,
                    text("title", 200, true), text("content", 8000, true)),
            spec("create_book_record", "记录一笔收入或支出。先问清分类、金额和收付款账户的准确名称，日期缺省今天。", true,
                    choice("type", true, "EXPENSE", "INCOME"), money(), text("category", 30, true),
                    text("account", 50, true), text("date", 10, false), text("remark", 200, false)),
            spec("create_wish", "为本家庭新建愿望，可附原因。", true, text("title", 100, true), text("reason", 500, false)),
            spec("notify_family", "发送一条站内通知。先查家庭成员确定收件人，姓名有歧义先问清。", true,
                    integer("memberId", Long.MAX_VALUE, true), text("message", 250, true)),
            spec("put_item", "新登记一件家庭物品及其位置。已登记物品的挪位或修改不支持。", true,
                    text("name", 100, true), text("position", 50, true)));

    private final SecurityHelper security;
    private final SysRoleMapper roles;
    private final AssistantSearchService search;
    private final ScheduleService schedule;
    private final ItemAiService itemAi;
    private final AssistantWrites writes;

    public List<Map<String, Object>> catalog() {
        return SPECS.stream().map(AssistantToolSpec::descriptor).toList();
    }

    private static AssistantToolSpec spec(String name, String description, boolean write, Field... fields) {
        return new AssistantToolSpec(name, description, write, List.of(fields));
    }

    private static AssistantToolSpec spec(String name) {
        return SPECS.stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow(AssistantToolSpec::invalid);
    }

    public boolean isWrite(String name) {
        return spec(name).write();
    }

    private LoginUser current(boolean write) {
        LoginUser principal = security.current();
        if (principal == null || principal.getFamilyId() == null) throw new BizException(ResultCode.FORBIDDEN);
        String role = roles.selectRoleCodeByUserAndFamily(principal.getUserId(), principal.getFamilyId());
        if (role == null || !Set.of("OWNER", "MEMBER", "CHILD", "GUEST").contains(role)) throw new BizException(ResultCode.FORBIDDEN);
        if (write && "GUEST".equals(role)) throw new BizException(ResultCode.FORBIDDEN, "访客只能查询家庭信息");
        return new LoginUser(principal.getUserId(), principal.getUsername(), role, principal.getFamilyId());
    }

    public Map<String, Object> read(String name, Map<String, Object> args) {
        if (isWrite(name)) throw invalid();
        LoginUser user = current(false);
        ViewerScope viewer = ViewerScope.of(user.getUserId(), user.getFamilyId(), "OWNER".equals(user.getRole()));
        Map<String, Object> a = spec(name).validate(args);
        return switch (name) {
            case "get_schedule" -> schedule(user, a);
            case "list_family_members" -> Map.of("items", search.members(user.getFamilyId()));
            case "search_content" -> Map.of("items", search.search(viewer, a));
            case "find_item" -> find(viewer, (String) a.get("query"));
            case "open_page" -> Map.of("page", a.get("page"), "path", PAGES.get(a.get("page")));
            default -> throw invalid();
        };
    }

    private Map<String, Object> schedule(LoginUser user, Map<String, Object> args) {
        LocalDate from = args.containsKey("from") ? date(args, "from") : LocalDate.now(ZONE);
        int days = ((Number) args.getOrDefault("days", 7)).intValue();
        var data = schedule.events(user.getFamilyId(), from, from.plusDays(days - 1L), false);
        List<Map<String, Object>> rows = new ArrayList<>();
        if (data.get("days") instanceof Map<?, ?> byDate) {
            for (Object group : byDate.values()) {
                if (!(group instanceof List<?> list)) continue;
                for (Object raw : list) {
                    if (raw instanceof Map<?, ?> row && !Boolean.TRUE.equals(row.get("shared"))) {
                        rows.add(project(row, "title", "date", "time", "kind", "done"));
                    }
                }
            }
        }
        return Map.of("total", rows.size(), "items", rows.stream().limit(50).toList());
    }

    private Map<String, Object> find(ViewerScope viewer, String query) {
        Map<String, Object> result = itemAi.findWithinFamily(viewer, query);
        List<Map<String, Object>> own = new ArrayList<>();
        if (result.get("matches") instanceof List<?> list) {
            for (Object raw : list) {
                if (raw instanceof Map<?, ?> m && m.get("family_id") instanceof Number f && f.longValue() == viewer.familyId()) {
                    own.add(project(m, "name", "position", "house_name", "room_name", "furniture_name", "quantity", "unit"));
                }
            }
        }
        return Map.of("reply", own.isEmpty() ? "没有找到相关物品" : "找到 " + own.size() + " 件相关物品", "matches", own);
    }

    private static Map<String, Object> project(Map<?, ?> row, String... keys) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String key : keys) if (row.get(key) != null) out.put(key, row.get(key));
        return out;
    }

    public Map<String, Object> prepare(String name, Map<String, Object> args) {
        if (!isWrite(name)) throw invalid();
        return writes.prepare(name, spec(name).validate(args), current(true));
    }

    public String preview(String name, Map<String, Object> normalized) {
        return writes.preview(name, normalized);
    }

    public Map<String, Object> execute(String name, Map<String, Object> normalized) {
        if (!isWrite(name)) throw invalid();
        return writes.execute(name, normalized, current(true), spec(name));
    }

    static LocalDate date(Map<String, Object> args, String key) {
        if (!args.containsKey(key)) return null;
        try {
            LocalDate result = LocalDate.parse((String) args.get(key));
            if (result.getYear() < 1000 || result.getYear() > 9998) throw invalid();
            return result;
        } catch (DateTimeException e) {
            throw new BizException(ResultCode.BAD_REQUEST, "请提供有效的公历日期");
        }
    }
}
