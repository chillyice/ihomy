package com.ihomy.service.assistant;

import com.ihomy.common.AnniversaryDate;
import com.ihomy.common.BizException;
import com.ihomy.common.DictConst;
import com.ihomy.common.ResultCode;
import com.ihomy.entity.Announcement;
import com.ihomy.entity.ContentBook;
import com.ihomy.security.LoginUser;
import com.ihomy.service.AnnouncementService;
import com.ihomy.service.AnniversaryService;
import com.ihomy.service.BookAccountService;
import com.ihomy.service.BookPermissionService;
import com.ihomy.service.BookService;
import com.ihomy.service.FamilyPlanService;
import com.ihomy.service.FamilyTreeService;
import com.ihomy.service.HealthQueryService;
import com.ihomy.service.HomeAssistantService;
import com.ihomy.service.LibraryService;
import com.ihomy.service.MusicService;
import com.ihomy.service.PointsService;
import com.ihomy.service.RecipeService;
import com.ihomy.service.TaskService;
import com.ihomy.service.VideoService;
import com.ihomy.service.access.ViewerScope;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Read-only tools for the modules outside the original chat scope.
 * Every handler reuses the same service and visibility rules as its page; identity comes from the server.
 */
@Service
@RequiredArgsConstructor
class AssistantReadTools {
    private final FamilyPlanService plans;
    private final AnniversaryService anniversaries;
    private final TaskService tasks;
    private final BookService book;
    private final BookPermissionService bookAccess;
    private final BookAccountService accounts;
    private final LibraryService library;
    private final VideoService videos;
    private final MusicService music;
    private final AnnouncementService announcements;
    private final RecipeService recipes;
    private final HomeAssistantService iot;
    private final PointsService points;
    private final FamilyTreeService trees;
    private final HealthQueryService health;

    Map<String, Object> handle(String name, Map<String, Object> a, LoginUser user, ViewerScope viewer) {
        return switch (name) {
            case "get_plans" -> plans(user, a);
            case "list_anniversaries" -> anniversaries(user, a);
            case "list_tasks" -> tasks(user, a);
            case "get_book_summary" -> bookSummary(user);
            case "list_book_records" -> bookRecords(user, a);
            case "list_library" -> books(viewer, a);
            case "list_videos" -> videos(viewer, a);
            case "list_music" -> music(user, a);
            case "list_announcements" -> announcements(user);
            case "search_recipes" -> recipes(user, a);
            case "list_iot_devices" -> devices(viewer, a);
            case "get_health_summary" -> health(user, a);
            case "get_points" -> points(user, a);
            case "list_family_tree" -> tree(user, a);
            default -> throw AssistantToolSpec.invalid();
        };
    }

    private Map<String, Object> plans(LoginUser user, Map<String, Object> a) {
        String scope = (String) a.getOrDefault("scope", "ALL");
        int limit = ((Number) a.getOrDefault("limit", 20)).intValue();
        Long family = user.getFamilyId();
        if ("TODAY".equals(scope)) {
            Map<String, Object> today = plans.today(family);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("plans", limit(today.get("plans"), limit));
            out.put("reminders", limit(today.get("reminders"), limit));
            out.put("undated", limit(today.get("undated"), limit));
            out.put("events", limit(today.get("events"), limit));
            return out;
        }
        if ("UNDATED".equals(scope)) return Map.of("items", limit(plans.undated(family), limit));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> p : plans.list(family)) {
            if (DictConst.PLAN_DONE.equals(p.get("status")) || DictConst.PLAN_CANCELLED.equals(p.get("status"))) continue;
            Map<String, Object> m = AssistantTools.project(p, "title", "targetDate", "status", "remindTime",
                    "doneCount", "totalCount", "assigneeName");
            if (p.get("tasks") instanceof List<?> list) {
                List<Map<String, Object>> items = new ArrayList<>();
                for (Object raw : list) {
                    if (raw instanceof Map<?, ?> t) items.add(AssistantTools.project(t, "title", "dueDate", "done"));
                }
                m.put("tasks", items);
            }
            rows.add(m);
            if (rows.size() >= limit) break;
        }
        return Map.of("total", rows.size(), "items", rows);
    }

    private Map<String, Object> anniversaries(LoginUser user, Map<String, Object> a) {
        int days = ((Number) a.getOrDefault("days", 60)).intValue();
        LocalDate today = LocalDate.now(AssistantTools.ZONE);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> v : anniversaries.list(user.getFamilyId(), true)) {
            LocalDate next = AnniversaryDate.nextOccurrence((String) v.get("calendar"),
                    number(v.get("month")), number(v.get("day")), number(v.get("isLeap")), today);
            if (next == null) continue;
            long until = ChronoUnit.DAYS.between(today, next);
            if (until > days) continue;
            Map<String, Object> m = AssistantTools.project(v, "name", "calendar", "month", "day", "memberNames");
            m.put("next", next.toString());
            m.put("daysUntil", until);
            rows.add(m);
        }
        rows.sort(Comparator.comparingLong(r -> ((Number) r.get("daysUntil")).longValue()));
        return Map.of("total", rows.size(), "items", rows);
    }

    private Map<String, Object> tasks(LoginUser user, Map<String, Object> a) {
        String status = (String) a.getOrDefault("status", "TODO");
        int limit = ((Number) a.getOrDefault("limit", 20)).intValue();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> t : tasks.list(user.getFamilyId())) {
            String current = (String) t.get("status");
            boolean match = "TODO".equals(status)
                    ? !DictConst.TASK_DONE.equals(current) && !DictConst.TASK_CANCELLED.equals(current)
                    : status.equals(current);
            if (!match) continue;
            rows.add(AssistantTools.project(t, "title", "status", "rewardType", "rewardPoints", "rewardItem",
                    "assigneeName", "creatorName", "createdAt"));
            if (rows.size() >= limit) break;
        }
        return Map.of("total", rows.size(), "items", rows);
    }

    private Map<String, Object> bookSummary(LoginUser user) {
        bookAccess.requireRead(user);
        Map<String, Object> out = new LinkedHashMap<>(book.summary(user.getFamilyId()));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> a : accounts.list(user.getFamilyId())) {
            rows.add(AssistantTools.project(a, "name", "accountType", "balance"));
        }
        out.put("accounts", rows);
        return out;
    }

    private Map<String, Object> bookRecords(LoginUser user, Map<String, Object> a) {
        bookAccess.requireRead(user);
        int limit = ((Number) a.getOrDefault("limit", 20)).intValue();
        String category = (String) a.get("category");
        Map<String, Object> data = book.list(user.getFamilyId(), (String) a.get("month"));
        List<Map<String, Object>> rows = new ArrayList<>();
        if (data.get("records") instanceof List<?> records) {
            for (Object raw : records) {
                if (!(raw instanceof Map<?, ?> r)) continue;
                if (category != null && !category.equals(r.get("category"))) continue;
                rows.add(AssistantTools.project(r, "recordDate", "type", "amount", "category", "remark",
                        "accountName", "fromAccountName", "toAccountName", "creatorName"));
                if (rows.size() >= limit) break;
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("month", a.containsKey("month") ? a.get("month") : LocalDate.now(AssistantTools.ZONE).toString().substring(0, 7));
        out.put("stats", data.get("stats"));
        out.put("items", rows);
        return out;
    }

    private Map<String, Object> books(ViewerScope viewer, Map<String, Object> a) {
        int limit = ((Number) a.getOrDefault("limit", 12)).intValue();
        var page = library.page(1, Math.min(limit, 20), viewer, (String) a.get("keyword"), null,
                (String) a.get("format"), null, null);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ContentBook b : page.getRecords()) {
            Map<String, Object> m = AssistantTools.project(Map.of(
                    "title", b.getTitle() == null ? "" : b.getTitle(),
                    "author", b.getAuthor() == null ? "" : b.getAuthor(),
                    "fileFormat", b.getFileFormat() == null ? "" : b.getFileFormat(),
                    "visibility", b.getVisibility() == null ? "" : b.getVisibility()), "title", "author", "fileFormat", "visibility");
            m.put("path", "/library");
            rows.add(m);
        }
        return Map.of("total", page.getTotal(), "items", rows);
    }

    private Map<String, Object> videos(ViewerScope viewer, Map<String, Object> a) {
        int limit = ((Number) a.getOrDefault("limit", 12)).intValue();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> v : videos.list(viewer, (String) a.get("keyword"), (String) a.get("mediaType"))) {
            Map<String, Object> m = AssistantTools.project(v, "title", "mediaType", "year", "rating", "duration", "episodes");
            m.put("path", "/cinema/" + v.get("id"));
            rows.add(m);
            if (rows.size() >= limit) break;
        }
        return Map.of("total", rows.size(), "items", rows);
    }

    private Map<String, Object> music(LoginUser user, Map<String, Object> a) {
        String keyword = ((String) a.getOrDefault("keyword", "")).toLowerCase(Locale.ROOT);
        List<Map<String, Object>> tracks = new ArrayList<>();
        for (Map<String, Object> t : music.listByFamily(user.getFamilyId())) {
            String text = (String.valueOf(t.get("title")) + " " + String.valueOf(t.get("artist")) + " "
                    + String.valueOf(t.get("album"))).toLowerCase(Locale.ROOT);
            if (!keyword.isEmpty() && !text.contains(keyword)) continue;
            tracks.add(AssistantTools.project(t, "title", "artist", "album", "duration"));
            if (tracks.size() >= 20) break;
        }
        List<Map<String, Object>> playlists = new ArrayList<>();
        for (var p : music.listPlaylists(user.getFamilyId())) {
            playlists.add(Map.of("name", p.getName(), "trackCount", p.getTrackCount() == null ? 0 : p.getTrackCount()));
        }
        List<String> albums = new ArrayList<>();
        for (Map<String, Object> al : music.albumsByFamily(user.getFamilyId())) {
            if (al.get("album") instanceof String name) albums.add(name);
        }
        return Map.of("tracks", tracks, "playlists", playlists, "albums", albums);
    }

    private Map<String, Object> announcements(LoginUser user) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Announcement a : announcements.list(user.getFamilyId(), false)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("title", a.getTitle());
            if (a.getLinkUrl() != null) m.put("linkUrl", a.getLinkUrl());
            if (a.getStartDate() != null) m.put("startDate", a.getStartDate().toString());
            if (a.getEndDate() != null) m.put("endDate", a.getEndDate().toString());
            rows.add(m);
        }
        return Map.of("total", rows.size(), "items", rows);
    }

    private Map<String, Object> recipes(LoginUser user, Map<String, Object> a) {
        String keyword = ((String) a.getOrDefault("keyword", "")).trim();
        Map<String, Object> menu = recipes.menu(user.getFamilyId());
        List<Map<String, Object>> briefs = new ArrayList<>();
        if (menu.get("groups") instanceof List<?> groups) {
            for (Object group : groups) {
                if (group instanceof Map<?, ?> g && g.get("items") instanceof List<?> items) {
                    for (Object item : items) {
                        if (item instanceof Map<?, ?> raw) {
                            Map<String, Object> brief = AssistantTools.project(raw, "id", "name", "category", "cuisine", "flavor", "authorName");
                            if (keyword.isEmpty() || String.valueOf(brief.get("name")).contains(keyword)) briefs.add(brief);
                        }
                    }
                }
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> brief : briefs) {
            if (rows.size() >= 3) break;
            Map<String, Object> detail = keyword.isEmpty() ? brief : recipes.detail(((Number) brief.get("id")).longValue(), user.getFamilyId());
            Map<String, Object> m = AssistantTools.project(detail, "name", "category", "cuisine", "flavor", "description",
                    "ingredients", "equipment", "steps");
            rows.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", keyword.isEmpty() ? limit(briefs, 10) : rows);
        if (keyword.isEmpty() && menu.get("todayRecommend") instanceof List<?> today) {
            out.put("todayRecommend", limit(today, 5));
        }
        return out;
    }

    private Map<String, Object> devices(ViewerScope viewer, Map<String, Object> a) {
        String room = (String) a.get("room");
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> d : iot.devices(viewer)) {
            if (room != null && !room.equals(d.get("room"))) continue;
            Map<String, Object> m = AssistantTools.project(d, "name", "room", "domain", "state", "unit", "controllable");
            rows.add(m);
        }
        return Map.of("total", rows.size(), "items", rows);
    }

    private Map<String, Object> health(LoginUser user, Map<String, Object> a) {
        int days = ((Number) a.getOrDefault("days", 7)).intValue();
        return health.summary(user.getUserId(), days);
    }

    private Map<String, Object> points(LoginUser user, Map<String, Object> a) {
        Map<String, Object> out = new LinkedHashMap<>(points.stats(user.getUserId(), user.getFamilyId()));
        if (Boolean.TRUE.equals(a.get("includeProducts"))) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Map<String, Object> p : points.products(user.getFamilyId(), user.getUserId())) {
                if (!Integer.valueOf(1).equals(number(p.get("enabled")))) continue;
                rows.add(AssistantTools.project(p, "name", "points", "stock", "perLimit", "redeemedCount"));
            }
            out.put("products", rows);
        }
        return out;
    }

    private Map<String, Object> tree(LoginUser user, Map<String, Object> a) {
        String name = (String) a.get("name");
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> m : trees.list(user.getFamilyId())) {
            if (name != null && !String.valueOf(m.get("name")).contains(name)) continue;
            rows.add(AssistantTools.project(m, "name", "gender", "birthDate", "fatherName", "motherName",
                    "spouseName", "userName", "generation"));
        }
        return Map.of("total", rows.size(), "items", rows);
    }

    private static Integer number(Object value) {
        return value instanceof Number n ? n.intValue() : null;
    }

    private static List<Map<String, Object>> limit(Object rows, int max) {
        if (!(rows instanceof List<?> list)) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object raw : list) {
            if (raw instanceof Map<?, ?> m) {
                Map<String, Object> copy = new LinkedHashMap<>();
                m.forEach((k, v) -> copy.put(String.valueOf(k), v));
                out.add(copy);
            }
            if (out.size() >= max) break;
        }
        return out;
    }
}
