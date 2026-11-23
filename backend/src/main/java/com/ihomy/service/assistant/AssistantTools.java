package com.ihomy.service.assistant;

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

    private final SecurityHelper security;
    private final SysRoleMapper roles;
    private final AssistantSearchService search;
    private final ScheduleService schedule;
    private final ItemAiService itemAi;
    private final AssistantReadTools reads;
    private final AssistantWrites writes;

    public List<Map<String, Object>> catalog() {
        return AssistantToolSpecs.SPECS.stream().map(AssistantToolSpec::descriptor).toList();
    }

    private static AssistantToolSpec spec(String name) {
        return AssistantToolSpecs.SPECS.stream().filter(s -> s.name().equals(name)).findFirst()
                .orElseThrow(AssistantToolSpec::invalid);
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
        if (isWrite(name)) throw AssistantToolSpec.invalid();
        LoginUser user = current(false);
        ViewerScope viewer = ViewerScope.of(user.getUserId(), user.getFamilyId(), "OWNER".equals(user.getRole()));
        Map<String, Object> a = spec(name).validate(args);
        return switch (name) {
            case "get_schedule" -> schedule(user, a);
            case "list_family_members" -> Map.of("items", search.members(user.getFamilyId()));
            case "search_content" -> Map.of("items", search.search(viewer, a));
            case "find_item" -> find(viewer, (String) a.get("query"));
            case "open_page" -> Map.of("page", a.get("page"), "path", AssistantToolSpecs.PAGES.get(a.get("page")));
            default -> reads.handle(name, a, user, viewer);
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

    static Map<String, Object> project(Map<?, ?> row, String... keys) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String key : keys) if (row.get(key) != null) out.put(key, row.get(key));
        return out;
    }

    public Map<String, Object> prepare(String name, Map<String, Object> args) {
        if (!isWrite(name)) throw AssistantToolSpec.invalid();
        return writes.prepare(name, spec(name).validate(args), current(true));
    }

    public String preview(String name, Map<String, Object> normalized) {
        return writes.preview(name, normalized);
    }

    public Map<String, Object> execute(String name, Map<String, Object> normalized) {
        if (!isWrite(name)) throw AssistantToolSpec.invalid();
        return writes.execute(name, normalized, current(true), spec(name));
    }

    static LocalDate date(Map<String, Object> args, String key) {
        if (!args.containsKey(key)) return null;
        try {
            LocalDate result = LocalDate.parse((String) args.get(key));
            if (result.getYear() < 1000 || result.getYear() > 9998) throw AssistantToolSpec.invalid();
            return result;
        } catch (DateTimeException e) {
            throw new BizException(ResultCode.BAD_REQUEST, "请提供有效的公历日期");
        }
    }
}
