package com.ihomy.service.assistant;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.DictConst;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.*;
import com.ihomy.entity.BookAccount;
import com.ihomy.mapper.BookAccountMapper;
import com.ihomy.security.LoginUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.*;
import com.ihomy.service.access.ViewerScope;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Prepare is read-only. Execute is invoked by the confirmation transaction with persisted arguments. */
@Service
@RequiredArgsConstructor
class AssistantWrites {
    private static final Set<String> SIMPLE_IOT_DOMAINS = Set.of("switch", "light", "fan", "humidifier", "input_boolean");
    private static final tools.jackson.databind.ObjectMapper JSON = new tools.jackson.databind.ObjectMapper();

    private final DiaryService diaries;
    private final BlogService blogs;
    private final WishService wishes;
    private final ReminderService reminders;
    private final BookService book;
    private final BookPermissionService bookAccess;
    private final BookAccountMapper accounts;
    private final AssistantSearchService search;
    private final NotificationService notifications;
    private final ItemLocalParser itemParser;
    private final ItemService items;
    private final FamilyPlanService plans;
    private final TaskService tasks;
    private final AnniversaryService anniversaries;
    private final RecipeService recipes;
    private final LibraryService library;
    private final HomeAssistantService iot;
    private final PointsService points;
    private final SecurityHelper security;

    Map<String, Object> prepare(String name, Map<String, Object> a, LoginUser user) {
        switch (name) {
            case "create_diary" -> {
                normalizeDate(a);
                a.put("visibility", DictConst.VIS_PRIVATE);
            }
            case "create_book_record" -> {
                bookAccess.requireWrite(user);
                normalizeDate(a);
                a.put("accountId", account(user.getFamilyId(), (String) a.get("account")).getId());
            }
            case "create_reminder" -> {
                AssistantTools.date(a, "date");
                clock(a, "time");
                a.putIfAbsent("repeat", DictConst.REPEAT_ONCE);
            }
            case "notify_family" -> a.put("recipient", search.recipient(user.getFamilyId(), id(a, "memberId")).get("name"));
            case "put_item" -> {
                // Resolve links without invoking an LLM or the existing move-or-create writer.
                ItemLocalParser.PutPlan plan = parseLocation(user, a);
                if (plan != null && plan.roomId() != null) a.put("roomId", plan.roomId());
                if (plan != null && plan.furnitureId() != null) a.put("furnitureId", plan.furnitureId());
            }
            case "create_plan" -> {
                if (a.containsKey("targetDate")) AssistantTools.date(a, "targetDate");
                if (a.containsKey("remindTime")) {
                    if (!a.containsKey("targetDate")) {
                        throw new BizException(ResultCode.BAD_REQUEST, "设置提醒时间需要先给出目标日期");
                    }
                    clock(a, "remindTime");
                }
            }
            case "add_plan_task" -> {
                a.put("planId", planId(user.getFamilyId(), (String) a.get("plan")));
                if (a.containsKey("dueDate")) AssistantTools.date(a, "dueDate");
                if (a.containsKey("assignee")) a.put("assigneeId", memberId(user.getFamilyId(), (String) a.get("assignee")));
            }
            case "create_task" -> normalizeReward(a);
            case "create_anniversary" -> normalizeAnniversary(a);
            case "task_action" -> {
                Map<String, Object> task = task(user.getFamilyId(), (String) a.get("task"));
                a.put("taskId", task.get("id"));
                a.put("taskStatus", task.get("status"));
                a.put("taskTitle", task.get("title"));
            }
            case "update_reading_status" -> {
                Map<String, Object> row = libraryBook(user, (String) a.get("book"));
                a.put("bookId", row.get("id"));
                a.put("bookTitle", row.get("title"));
            }
            case "iot_control" -> resolveDevice(user, a);
            case "redeem_points" -> {
                Map<String, Object> product = product(user, (String) a.get("product"));
                a.put("productId", product.get("id"));
                a.put("productPoints", product.get("points"));
            }
            case "check_in" -> {
                if (Boolean.TRUE.equals(points.stats(user.getUserId(), user.getFamilyId()).get("checkedToday"))) {
                    throw new BizException(ResultCode.ALREADY_CHECKIN);
                }
            }
            case "take_item" -> {
                Map<String, Object> item = item(user, (String) a.get("name"));
                BigDecimal amount = decimal(a.get("amount"));
                BigDecimal stock = item.get("quantity") instanceof Number n ? new BigDecimal(n.toString()) : null;
                if (stock != null && amount.compareTo(stock) > 0) {
                    throw new BizException(ResultCode.BAD_REQUEST, "取出数量超过现有库存");
                }
                a.put("itemId", item.get("id"));
                a.put("unit", item.get("unit"));
                a.put("stock", stock);
            }
            default -> { /* Blog, wish and recipe already contain their complete normalized form. */ }
        }
        return a;
    }

    private void normalizeReward(Map<String, Object> a) {
        String reward = (String) a.getOrDefault("rewardType", DictConst.REWARD_NONE);
        if (DictConst.REWARD_POINTS.equals(reward) && !a.containsKey("rewardPoints")) {
            throw new BizException(ResultCode.BAD_REQUEST, "积分奖励请填写积分数");
        }
        if (DictConst.REWARD_ITEM.equals(reward) && !a.containsKey("rewardItem")) {
            throw new BizException(ResultCode.BAD_REQUEST, "物品奖励请填写奖励内容");
        }
        if (DictConst.REWARD_NONE.equals(reward)) {
            a.remove("rewardPoints");
            a.remove("rewardItem");
        }
        a.put("rewardType", reward);
    }

    private void normalizeAnniversary(Map<String, Object> a) {
        String calendar = (String) a.getOrDefault("calendar", "solar");
        int month = ((Number) a.get("month")).intValue();
        int day = ((Number) a.get("day")).intValue();
        if ("lunar".equals(calendar)) {
            if (day > 30) throw new BizException(ResultCode.BAD_REQUEST, "农历日期只有 1-30");
        } else {
            try {
                LocalDate.of(2024, month, day);
            } catch (DateTimeException e) {
                throw new BizException(ResultCode.BAD_REQUEST, "请提供有效的公历月份和日期");
            }
        }
        boolean remind = a.containsKey("remindDaysBefore");
        if (remind && a.containsKey("remindTime")) clock(a, "remindTime");
        else if (remind) a.put("remindTime", "08:00");
        else if (a.containsKey("remindTime")) throw new BizException(ResultCode.BAD_REQUEST, "设置提醒时刻需要先设置提前提醒天数");
        a.put("calendar", calendar);
        a.put("recurring", !Boolean.FALSE.equals(a.get("recurring")));
    }

    private void resolveDevice(LoginUser user, Map<String, Object> a) {
        if (!security.hasPermission("iot:control")) throw new BizException(ResultCode.FORBIDDEN, "没有控制智能家居的权限");
        String name = (String) a.get("device");
        String room = (String) a.get("room");
        List<Map<String, Object>> matches = new ArrayList<>();
        for (Map<String, Object> d : iot.devices(viewer(user))) {
            if (!name.equals(d.get("name")) || (room != null && !room.equals(d.get("room")))) continue;
            matches.add(d);
        }
        if (matches.isEmpty()) throw new BizException(ResultCode.NOT_FOUND, "找不到这个智能设备，请先在智能家居页确认名称");
        if (matches.size() > 1) throw new BizException(ResultCode.BAD_REQUEST, "有多个同名设备，请补充所在房间");
        Map<String, Object> d = matches.getFirst();
        if (!Boolean.TRUE.equals(d.get("controllable"))) {
            throw new BizException(ResultCode.BAD_REQUEST, "这个设备暂不支持直接开关");
        }
        if (!SIMPLE_IOT_DOMAINS.contains(String.valueOf(d.get("domain")))) {
            throw new BizException(ResultCode.BAD_REQUEST, "这个设备需要到智能家居页调整");
        }
        String service = switch ((String) a.get("action")) {
            case "ON" -> "turn_on";
            case "OFF" -> "turn_off";
            default -> "toggle";
        };
        a.put("entityId", d.get("entityId"));
        a.put("service", service);
        a.put("deviceName", d.get("name"));
    }

    private Map<String, Object> task(Long familyId, String title) {
        List<Map<String, Object>> matches = new ArrayList<>();
        for (Map<String, Object> t : tasks.list(familyId)) {
            if (title.equals(t.get("title"))) matches.add(t);
        }
        return one(matches, "找不到这个任务，请核对标题", "有多个同名任务，请先到任务页区分");
    }

    private Map<String, Object> libraryBook(LoginUser user, String title) {
        var page = library.page(1, 20, viewer(user), title, null, null, null, null);
        List<Map<String, Object>> matches = new ArrayList<>();
        for (var b : page.getRecords()) {
            if (title.equals(b.getTitle())) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", b.getId());
                m.put("title", b.getTitle());
                matches.add(m);
            }
        }
        return one(matches, "找不到这本书，请核对书名", "书库中有多本同名书，请先到阅读页区分");
    }

    private Map<String, Object> product(LoginUser user, String name) {
        List<Map<String, Object>> matches = new ArrayList<>();
        for (Map<String, Object> p : points.products(user.getFamilyId(), user.getUserId())) {
            if (name.equals(p.get("name")) && Integer.valueOf(1).equals(p.get("enabled"))) matches.add(p);
        }
        return one(matches, "找不到这个可兑换商品，请先到积分页查看", "有多个同名商品，请先到积分页区分");
    }

    private Map<String, Object> item(LoginUser user, String name) {
        List<Map<String, Object>> matches = new ArrayList<>();
        for (Map<String, Object> row : itemParser.findLocal(viewer(user), name)) {
            if (name.equals(row.get("name"))) matches.add(row);
        }
        return one(matches, "找不到这件物品，请核对名称", "有多个同名物品，请先到物品页区分");
    }

    private static Map<String, Object> one(List<Map<String, Object>> matches, String missing, String ambiguous) {
        if (matches.isEmpty()) throw new BizException(ResultCode.NOT_FOUND, missing);
        if (matches.size() > 1) throw new BizException(ResultCode.BAD_REQUEST, ambiguous);
        return matches.getFirst();
    }

    private Long planId(Long familyId, String title) {
        List<Map<String, Object>> matches = new ArrayList<>();
        for (Map<String, Object> p : plans.list(familyId)) {
            if (title.equals(p.get("title")) && !DictConst.PLAN_DONE.equals(p.get("status"))
                    && !DictConst.PLAN_CANCELLED.equals(p.get("status"))) matches.add(p);
        }
        return ((Number) one(matches, "找不到这个计划，请核对标题", "有多个同名计划，请先到计划页区分").get("id")).longValue();
    }

    private Long memberId(Long familyId, String name) {
        List<Map<String, Object>> matches = new ArrayList<>();
        for (Map<String, Object> m : search.members(familyId)) {
            if (name.equals(m.get("name"))) matches.add(m);
        }
        return ((Number) one(matches, "找不到这位家庭成员", "有多个同名成员，请先说明是哪一位").get("id")).longValue();
    }

    private ItemLocalParser.PutPlan parseLocation(LoginUser user, Map<String, Object> a) {
        return itemParser.parsePut(user.getFamilyId(), "把" + a.get("name") + "放在" + a.get("position"));
    }

    private void normalizeDate(Map<String, Object> a) {
        a.putIfAbsent("date", LocalDate.now(AssistantTools.ZONE).toString());
        AssistantTools.date(a, "date");
    }

    private LocalTime clock(Map<String, Object> a, String key) {
        try {
            return LocalTime.parse((String) a.get(key));
        } catch (DateTimeException e) {
            throw new BizException(ResultCode.BAD_REQUEST, "请输入有效的时刻，例如 09:00");
        }
    }

    private BookAccount account(Long familyId, String name) {
        List<BookAccount> matches = accounts.selectList(new LambdaQueryWrapper<BookAccount>()
                .select(BookAccount::getId, BookAccount::getName)
                .eq(BookAccount::getFamilyId, familyId).eq(BookAccount::getName, name).last("LIMIT 2"));
        if (matches.isEmpty()) throw new BizException(ResultCode.NOT_FOUND, "找不到这个账本账户，请核对账户名称");
        if (matches.size() != 1) throw new BizException(ResultCode.BAD_REQUEST, "账本中有多个同名账户，请先在账本中区分名称");
        return matches.getFirst();
    }

    private static Long id(Map<String, Object> a, String field) {
        return a.get(field) instanceof Number n ? n.longValue() : null;
    }

    private static BigDecimal decimal(Object value) {
        return value instanceof BigDecimal d ? d : new BigDecimal(String.valueOf(value));
    }

    private static ViewerScope viewer(LoginUser user) {
        return ViewerScope.of(user.getUserId(), user.getFamilyId(), "OWNER".equals(user.getRole()));
    }

    String preview(String name, Map<String, Object> a) {
        return switch (name) {
            case "create_diary" -> a.get("date") + " · 日记\n" + a.get("content");
            case "create_blog" -> "博客草稿（本家庭可见）· " + a.get("title") + "\n" + a.get("content");
            case "create_wish" -> "家庭愿望 · " + a.get("title") + optional(a, "reason");
            case "create_book_record" -> a.get("date") + " · " + (DictConst.BOOK_INCOME.equals(a.get("type")) ? "收入" : "支出")
                    + " " + a.get("amount") + " 元 · " + a.get("category") + " · " + a.get("account") + optional(a, "remark");
            case "notify_family" -> "通知 " + a.get("recipient") + "\n" + a.get("message");
            case "put_item" -> "新增家庭物品 · " + a.get("name") + "\n存放位置：" + a.get("position");
            case "create_reminder" -> a.get("date") + " " + a.get("time") + " · "
                    + switch ((String) a.get("repeat")) {
                        case "DAILY" -> "每天";
                        case "WEEKLY" -> "每周";
                        case "MONTHLY" -> "每月";
                        case "YEARLY" -> "每年";
                        default -> "一次";
                    } + " · " + a.get("title");
            case "create_plan" -> "家庭计划 · " + a.get("title")
                    + (a.containsKey("targetDate") ? "\n目标日期：" + a.get("targetDate") : "\n暂不设日期")
                    + (a.containsKey("remindTime") ? " " + a.get("remindTime") + " 提醒" : "")
                    + optional(a, "description");
            case "add_plan_task" -> "计划「" + a.get("plan") + "」新增子任务 · " + a.get("title")
                    + (a.containsKey("dueDate") ? "\n截止：" + a.get("dueDate") : "")
                    + (a.containsKey("assignee") ? "\n指派给：" + a.get("assignee") : "");
            case "create_task" -> "家庭任务 · " + a.get("title") + rewardText(a) + optional(a, "description");
            case "create_anniversary" -> anniversaryText(a);
            case "create_recipe" -> "菜谱 · " + a.get("name")
                    + (a.containsKey("category") ? "（" + a.get("category") + "）" : "")
                    + (a.containsKey("ingredients") ? "\n用料：" + lineCount((String) a.get("ingredients")) + " 项" : "")
                    + (a.containsKey("steps") ? "，步骤：" + lineCount((String) a.get("steps")) + " 步" : "");
            case "task_action" -> actionText((String) a.get("action")) + " · 任务「" + a.get("task") + "」";
            case "update_reading_status" -> readingText((String) a.get("status")) + " · 《" + a.get("book") + "》";
            case "iot_control" -> iotText(a);
            case "redeem_points" -> "积分兑换 · " + a.get("product") + "（" + a.get("productPoints") + " 积分）";
            case "check_in" -> "每日签到，领取今日积分";
            case "take_item" -> "取出物品 · " + a.get("name") + " " + a.get("amount")
                    + (a.containsKey("unit") && !String.valueOf(a.get("unit")).isBlank() ? " " + a.get("unit") : "")
                    + (a.get("stock") == null ? "" : "（现有 " + a.get("stock") + "）");
            default -> throw AssistantToolSpec.invalid();
        };
    }

    private static String rewardText(Map<String, Object> a) {
        return switch ((String) a.getOrDefault("rewardType", DictConst.REWARD_NONE)) {
            case "POINTS" -> "\n奖励：" + a.get("rewardPoints") + " 积分";
            case "ITEM" -> "\n奖励：" + a.get("rewardItem");
            default -> "\n无奖励";
        };
    }

    private static String anniversaryText(Map<String, Object> a) {
        String when = ("lunar".equals(a.get("calendar")) ? "农历 " : "阳历 ") + a.get("month") + " 月 " + a.get("day") + " 日";
        String remind = a.containsKey("remindDaysBefore")
                ? "\n提前 " + a.get("remindDaysBefore") + " 天 " + a.get("remindTime") + " 提醒" : "";
        return "纪念日 · " + a.get("name") + "\n" + when + (Boolean.FALSE.equals(a.get("recurring")) ? "（仅一次）" : "（每年）") + remind;
    }

    private static String actionText(String action) {
        return switch (action) {
            case "CLAIM" -> "领取任务";
            case "ABANDON" -> "放弃任务";
            case "FINISH" -> "申报完成";
            default -> "确认完成";
        };
    }

    private static String readingText(String status) {
        return switch (status) {
            case "READING" -> "标记在读";
            case "FINISHED" -> "标记读完";
            default -> "加入想读";
        };
    }

    private static String iotText(Map<String, Object> a) {
        String action = switch ((String) a.get("action")) {
            case "ON" -> "打开";
            case "OFF" -> "关闭";
            default -> "切换";
        };
        return action + "设备 · " + a.get("deviceName");
    }

    private static int lineCount(String text) {
        return (int) java.util.Arrays.stream(text.split("\\R")).map(String::trim).filter(s -> !s.isEmpty()).count();
    }

    private static String optional(Map<String, Object> a, String key) {
        return a.containsKey(key) ? "\n" + a.get(key) : "";
    }

    Map<String, Object> execute(String name, Map<String, Object> saved, LoginUser user, AssistantToolSpec spec) {
        Map<String, Object> source = new LinkedHashMap<>(saved);
        // Only these server-derived fields can occur in addition to the model's validated input.
        switch (name) {
            case "create_diary" -> source.remove("visibility");
            case "create_book_record" -> source.remove("accountId");
            case "notify_family" -> source.remove("recipient");
            case "put_item" -> {
                source.remove("roomId");
                source.remove("furnitureId");
            }
            case "add_plan_task" -> {
                source.remove("planId");
                source.remove("assigneeId");
            }
            case "create_anniversary" -> {
                source.remove("calendar");
                source.remove("recurring");
                source.remove("remindTime");
            }
            case "task_action" -> {
                source.remove("taskId");
                source.remove("taskStatus");
                source.remove("taskTitle");
            }
            case "update_reading_status" -> {
                source.remove("bookId");
                source.remove("bookTitle");
            }
            case "iot_control" -> {
                source.remove("entityId");
                source.remove("service");
                source.remove("deviceName");
            }
            case "redeem_points" -> {
                source.remove("productId");
                source.remove("productPoints");
            }
            case "take_item" -> {
                source.remove("itemId");
                source.remove("unit");
                source.remove("stock");
            }
            default -> { /* No derived fields. */ }
        }
        Map<String, Object> a = spec.validate(source);
        Long uid = user.getUserId();
        Long family = user.getFamilyId();
        return switch (name) {
            case "create_diary" -> {
                String visibility = (String) saved.get("visibility");
                if (!DictConst.VIS_PRIVATE.equals(visibility) && !DictConst.VIS_FAMILY.equals(visibility))
                    throw AssistantToolSpec.invalid();
                DiaryDTO dto = new DiaryDTO();
                dto.setDate(AssistantTools.date(a, "date").toString());
                dto.setContent((String) a.get("content"));
                dto.setVisibility(visibility);
                var created = diaries.create(uid, family, dto);
                yield result(created.getId(), "/diary/book/" + uid + "?entry=" + created.getId());
            }
            case "create_blog" -> {
                BlogDTO dto = new BlogDTO();
                dto.setTitle((String) a.get("title"));
                dto.setContent((String) a.get("content"));
                dto.setStatus(DictConst.BLOG_DRAFT);
                dto.setVisibility(DictConst.VIS_FAMILY);
                var created = blogs.create(uid, family, dto);
                yield result(created.getId(), "/blog/edit/" + created.getId());
            }
            case "create_reminder" -> {
                ReminderDTO dto = new ReminderDTO();
                dto.setTitle((String) a.get("title"));
                dto.setRemindDate(AssistantTools.date(a, "date"));
                dto.setRemindTime(clock(a, "time"));
                dto.setRepeatType((String) a.get("repeat"));
                dto.setCalendar("solar");
                yield result(reminders.create(uid, family, dto).getId(), "/plan");
            }
            case "create_wish" -> {
                WishDTO dto = new WishDTO();
                dto.setTitle((String) a.get("title"));
                dto.setReason((String) a.get("reason"));
                dto.setVisibility(DictConst.VIS_FAMILY);
                yield result(wishes.create(uid, family, dto).getId(), "/wish");
            }
            case "create_book_record" -> {
                bookAccess.requireWrite(user);
                Long accountId = id(saved, "accountId");
                if (!account(family, (String) a.get("account")).getId().equals(accountId))
                    throw new BizException(ResultCode.CONFLICT, "账本账户已变更，请重新确认");
                BookDTO dto = new BookDTO();
                dto.setAccountId(accountId);
                dto.setType((String) a.get("type"));
                dto.setAmount((BigDecimal) a.get("amount"));
                dto.setCategory((String) a.get("category"));
                dto.setRecordDate(AssistantTools.date(a, "date"));
                dto.setRemark((String) a.get("remark"));
                yield result(book.create(uid, family, dto).getId(), "/book");
            }
            case "notify_family" -> {
                Long recipientId = id(a, "memberId");
                search.recipient(family, recipientId);
                if (!notifications.createInFamily(recipientId, family, "family_notice", (String) a.get("message"), uid, null, null))
                    throw new BizException(ResultCode.NOT_FOUND, "找不到这位家庭成员");
                yield Map.of("delivered", true);
            }
            case "put_item" -> {
                ItemLocalParser.PutPlan plan = parseLocation(user, a);
                if (!Objects.equals(plan == null ? null : plan.roomId(), id(saved, "roomId"))
                        || !Objects.equals(plan == null ? null : plan.furnitureId(), id(saved, "furnitureId")))
                    throw new BizException(ResultCode.CONFLICT, "存放位置已变更，请重新确认");
                ItemDTO dto = new ItemDTO();
                dto.setName((String) a.get("name"));
                dto.setPosition((String) a.get("position"));
                dto.setRoomId(id(saved, "roomId"));
                dto.setFurnitureId(id(saved, "furnitureId"));
                dto.setVisibility(DictConst.VIS_FAMILY);
                yield result(items.itemCreate(viewer(user), dto).getId(), "/item");
            }
            case "create_plan" -> {
                PlanDTO dto = new PlanDTO();
                dto.setTitle((String) a.get("title"));
                dto.setDescription((String) a.get("description"));
                dto.setTargetDate(AssistantTools.date(a, "targetDate"));
                dto.setRemindTime((String) a.get("remindTime"));
                yield result(plans.create(uid, family, dto).getId(), "/plan");
            }
            case "add_plan_task" -> {
                Long planId = id(saved, "planId");
                if (!planId.equals(planId(family, (String) a.get("plan"))))
                    throw new BizException(ResultCode.CONFLICT, "计划已变更，请重新确认");
                PlanTaskDTO dto = new PlanTaskDTO();
                dto.setTitle((String) a.get("title"));
                dto.setDueDate(AssistantTools.date(a, "dueDate"));
                dto.setAssigneeId(id(saved, "assigneeId"));
                yield result(plans.addTask(planId, family, dto).getId(), "/plan");
            }
            case "create_task" -> {
                TaskDTO dto = new TaskDTO();
                dto.setTitle((String) a.get("title"));
                dto.setDescription((String) a.get("description"));
                dto.setRewardType((String) a.get("rewardType"));
                dto.setRewardPoints(a.get("rewardPoints") == null ? null : ((Number) a.get("rewardPoints")).intValue());
                dto.setRewardItem((String) a.get("rewardItem"));
                yield result(tasks.create(uid, family, dto).getId(), "/task");
            }
            case "create_anniversary" -> {
                AnniversaryDTO dto = new AnniversaryDTO();
                dto.setName((String) a.get("name"));
                dto.setCalendar((String) saved.get("calendar"));
                dto.setMonth(((Number) a.get("month")).intValue());
                dto.setDay(((Number) a.get("day")).intValue());
                dto.setRecurring(Boolean.FALSE.equals(saved.get("recurring")) ? 0 : 1);
                if (saved.get("remindDaysBefore") != null) {
                    dto.setRemindDaysBefore(((Number) saved.get("remindDaysBefore")).intValue());
                    dto.setRemindTime(LocalTime.parse((String) saved.get("remindTime")));
                }
                yield result(anniversaries.create(uid, family, dto).getId(), "/plan?tab=anniversary");
            }
            case "create_recipe" -> {
                RecipeDTO dto = new RecipeDTO();
                dto.setName((String) a.get("name"));
                dto.setCategory((String) a.get("category"));
                dto.setCuisine((String) a.get("cuisine"));
                dto.setFlavor((String) a.get("flavor"));
                dto.setDescription((String) a.get("description"));
                dto.setIngredients(lines((String) a.get("ingredients"), "name"));
                dto.setEquipment(lines((String) a.get("equipment"), "name"));
                dto.setSteps(lines((String) a.get("steps"), "content"));
                var created = recipes.create(uid, family, dto);
                yield result(created.getId(), "/kitchen/recipe/" + created.getId());
            }
            case "task_action" -> {
                Long taskId = id(saved, "taskId");
                Map<String, Object> task = task(family, (String) a.get("task"));
                if (!taskId.equals(task.get("id"))) throw new BizException(ResultCode.CONFLICT, "任务已变更，请重新确认");
                switch ((String) a.get("action")) {
                    case "CLAIM" -> tasks.claim(taskId, family, uid);
                    case "ABANDON" -> tasks.abandon(taskId, family, uid);
                    case "FINISH" -> tasks.finish(taskId, family, uid);
                    default -> tasks.confirm(taskId, family, uid);
                }
                yield Map.of("done", true, "path", "/task");
            }
            case "update_reading_status" -> {
                Long bookId = id(saved, "bookId");
                Map<String, Object> row = libraryBook(user, (String) a.get("book"));
                if (!bookId.equals(row.get("id"))) throw new BizException(ResultCode.CONFLICT, "书籍已变更，请重新确认");
                library.updateBorrowStatus(bookId, uid, family, (String) a.get("status"), null, null);
                yield Map.of("done", true, "path", "/library");
            }
            case "iot_control" -> {
                resolveDevice(user, a);
                if (!Objects.equals(a.get("entityId"), saved.get("entityId"))
                        || !Objects.equals(a.get("service"), saved.get("service")))
                    throw new BizException(ResultCode.CONFLICT, "设备状态已变更，请重新确认");
                IotControlDTO dto = new IotControlDTO();
                dto.setEntityId((String) saved.get("entityId"));
                dto.setService((String) saved.get("service"));
                Map<String, Object> state = iot.control(viewer(user), dto);
                yield Map.of("name", state.getOrDefault("name", saved.get("deviceName")),
                        "state", state.getOrDefault("state", ""), "path", "/iot");
            }
            case "redeem_points" -> {
                Long productId = id(saved, "productId");
                Map<String, Object> product = product(user, (String) a.get("product"));
                if (!productId.equals(product.get("id"))) throw new BizException(ResultCode.CONFLICT, "商品已变更，请重新确认");
                var order = points.redeem(productId, family, uid);
                yield result(order.getId(), "/points");
            }
            case "check_in" -> {
                Map<String, Object> state = points.checkin(uid, family);
                yield Map.of("points", state.getOrDefault("points", 0), "streak", state.getOrDefault("streak", 0),
                        "balance", state.getOrDefault("balance", 0), "path", "/points");
            }
            case "take_item" -> {
                Long itemId = id(saved, "itemId");
                Map<String, Object> row = item(user, (String) a.get("name"));
                if (!itemId.equals(row.get("id"))) throw new BizException(ResultCode.CONFLICT, "物品已变更，请重新确认");
                BigDecimal amount = (BigDecimal) a.get("amount");
                items.takeItem(itemId, amount, viewer(user));
                yield Map.of("name", a.get("name"), "amount", amount, "path", "/item");
            }
            default -> throw AssistantToolSpec.invalid();
        };
    }

    private static String lines(String text, String key) {
        List<Map<String, String>> rows = new ArrayList<>();
        if (text != null) {
            for (String line : text.split("\\R")) {
                String value = line.trim();
                if (value.isEmpty()) continue;
                rows.add(Map.of(key, value));
                if (rows.size() >= 60) break;
            }
        }
        return JSON.writeValueAsString(rows);
    }

    private static Map<String, Object> result(Long id, String path) {
        return Map.of("id", id, "path", path);
    }
}
