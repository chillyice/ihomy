package com.ihomy.service.assistant;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.DictConst;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.*;
import com.ihomy.entity.BookAccount;
import com.ihomy.mapper.BookAccountMapper;
import com.ihomy.security.LoginUser;
import com.ihomy.service.*;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Prepare is read-only. Execute is invoked by the confirmation transaction with persisted arguments. */
@Service
@RequiredArgsConstructor
class AssistantWrites {
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
                time(a);
                a.putIfAbsent("repeat", DictConst.REPEAT_ONCE);
            }
            case "notify_family" -> a.put("recipient", search.recipient(user.getFamilyId(), id(a, "memberId")).get("name"));
            case "put_item" -> {
                // Resolve links without invoking an LLM or the existing move-or-create writer.
                ItemLocalParser.PutPlan plan = parseLocation(user, a);
                if (plan != null && plan.roomId() != null) a.put("roomId", plan.roomId());
                if (plan != null && plan.furnitureId() != null) a.put("furnitureId", plan.furnitureId());
            }
            default -> { /* Blog and wish already contain their complete normalized form. */ }
        }
        return a;
    }

    private ItemLocalParser.PutPlan parseLocation(LoginUser user, Map<String, Object> a) {
        return itemParser.parsePut(user.getFamilyId(), "把" + a.get("name") + "放在" + a.get("position"));
    }

    private void normalizeDate(Map<String, Object> a) {
        a.putIfAbsent("date", LocalDate.now(AssistantTools.ZONE).toString());
        AssistantTools.date(a, "date");
    }

    private LocalTime time(Map<String, Object> a) {
        try {
            return LocalTime.parse((String) a.get("time"));
        } catch (DateTimeException e) {
            throw new BizException(ResultCode.BAD_REQUEST, "请输入有效的提醒时刻，例如 09:00");
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
            default -> throw AssistantToolSpec.invalid();
        };
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
                dto.setRemindTime(time(a));
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
                yield result(items.itemCreate(uid, family, dto).getId(), "/item");
            }
            default -> throw AssistantToolSpec.invalid();
        };
    }

    private static Map<String, Object> result(Long id, String path) {
        return Map.of("id", id, "path", path);
    }
}
