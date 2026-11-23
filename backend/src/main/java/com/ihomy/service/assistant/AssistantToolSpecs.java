package com.ihomy.service.assistant;

import static com.ihomy.service.assistant.AssistantToolSpec.*;

import java.util.List;
import java.util.Map;

/** Tool catalog shared by the model schema and server validation. Keep every rule here, not in prompts. */
final class AssistantToolSpecs {
    private AssistantToolSpecs() {
    }

    // Explicit subset of NAV_PATHS. No arbitrary path, query, privileged or sensitive destination.
    static final Map<String, String> PAGES = Map.ofEntries(
            Map.entry("album", "/album"), Map.entry("anniversary", "/plan?tab=anniversary"),
            Map.entry("announcement", "/announcement"), Map.entry("blog", "/blog"), Map.entry("book", "/book"),
            Map.entry("chat", "/chat"), Map.entry("cinema", "/cinema"), Map.entry("diary", "/diary"),
            Map.entry("games", "/games"), Map.entry("help", "/help"),
            Map.entry("item", "/item"), Map.entry("kitchen", "/kitchen"), Map.entry("library", "/library"),
            Map.entry("member", "/member"), Map.entry("music", "/music"), Map.entry("notification", "/notification"),
            Map.entry("plan", "/plan"), Map.entry("plant", "/tools/plant"), Map.entry("points", "/points"),
            Map.entry("reminder", "/plan?tab=today"), Map.entry("settings", "/settings"),
            Map.entry("storage", "/storage/files"), Map.entry("task", "/task"), Map.entry("tools", "/tools"),
            Map.entry("tree", "/member?tab=tree"), Map.entry("wish", "/wish"));

    static final List<AssistantToolSpec> SPECS = List.of(
            // ---------- read ----------
            spec("get_schedule", "查本人家庭日程。from 是起始公历日期 YYYY-MM-DD，缺省今天；days 为天数，缺省 7。查今天传 1。", false,
                    text("from", 10, false), integer("days", 31, false)),
            spec("find_item", "按用户原话找物，仅从本人可见的物品中返回名称和存放位置。", false, text("query", 300, true)),
            spec("list_family_members", "列出本家庭成员的姓名、角色和编号；通知或指派前用它定位成员。", false),
            spec("search_content", "在本家庭查可见内容。一般须提供 keyword；找自己的日记用 kind=diary 和 mine=true，可按 from/to 限定日期。", false,
                    choice("kind", false, "blog", "diary", "album", "wish", "item", "member"),
                    text("keyword", 100, false), integer("limit", 10, false), text("from", 10, false),
                    text("to", 10, false), flag("mine")),
            spec("open_page", "给用户一个站内功能按钮，由用户点击打开。只接受模块名。", false,
                    choice("page", true, PAGES.keySet().stream().sorted().toArray(String[]::new))),
            spec("get_plans", "查家庭计划。scope=TODAY 看今天到期、已逾期和今日提醒；ALL 看进行中的计划；UNDATED 看未定日期的计划与子任务。", false,
                    choice("scope", false, "TODAY", "ALL", "UNDATED"), integer("limit", 30, false)),
            spec("list_anniversaries", "查即将到来的纪念日与生日。days 是往后看的天数，缺省 60，最多 366。", false,
                    integer("days", 366, false)),
            spec("list_tasks", "查家庭悬赏任务。status 缺省 TODO（未结束），可只看 OPEN/IN_PROGRESS/REVIEW/DONE/CANCELLED。", false,
                    choice("status", false, "TODO", "OPEN", "IN_PROGRESS", "REVIEW", "DONE", "CANCELLED"),
                    integer("limit", 30, false)),
            spec("get_book_summary", "查本家庭账本本月收支概览与各账户余额。需要账本读取权限。", false),
            spec("list_book_records", "查账本明细。month 为 YYYY-MM，缺省本月；category 可按分类过滤。", false,
                    text("month", 7, false), text("category", 30, false), integer("limit", 50, false)),
            spec("list_library", "查家庭电子书。可用 keyword 搜书名或作者，format 过滤文件格式。", false,
                    text("keyword", 100, false), text("format", 10, false), integer("limit", 20, false)),
            spec("list_videos", "查家庭影院影片。可用 keyword 搜片名，mediaType 过滤类型。", false,
                    text("keyword", 100, false), text("mediaType", 20, false), integer("limit", 20, false)),
            spec("list_music", "查家庭音乐与歌单。可用 keyword 搜歌曲、歌手或专辑。", false, text("keyword", 100, false)),
            spec("list_announcements", "查家庭公告。", false),
            spec("search_recipes", "查家庭菜谱。keyword 可搜菜名；给出菜名时返回用料与做法。", false, text("keyword", 100, false)),
            spec("list_iot_devices", "查家庭智能设备的当前状态。room 可过滤房间。", false, text("room", 50, false)),
            spec("get_health_summary", "查本人最近的健康摘要（睡眠、步数、活动等）。只能查自己，天数缺省 7，最多 31。", false,
                    integer("days", 31, false)),
            spec("get_points", "查本人积分余额、签到状态与可兑换商品。", false, flag("includeProducts")),
            spec("list_family_tree", "查家谱成员及父母、配偶关系。name 可过滤姓名。", false, text("name", 50, false)),
            // ---------- write ----------
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
                    text("name", 100, true), text("position", 50, true)),
            spec("create_plan", "新建家庭计划。可带目标日期；要提醒时必须同时给出目标日期与 HH:mm。", true,
                    text("title", 100, true), text("description", 500, false), text("targetDate", 10, false),
                    text("remindTime", 5, false)),
            spec("add_plan_task", "给已有计划加一项子任务。plan 用计划标题，assignee 可填家人姓名。", true,
                    text("plan", 100, true), text("title", 100, true), text("dueDate", 10, false),
                    text("assignee", 50, false)),
            spec("create_task", "发布家庭悬赏任务。奖励可选无奖励、积分或自定义物品。", true,
                    text("title", 100, true), text("description", 500, false),
                    choice("rewardType", false, "NONE", "POINTS", "ITEM"), integer("rewardPoints", 100000, false),
                    text("rewardItem", 100, false)),
            spec("create_anniversary", "新建纪念日或生日。给出公历的月份与日期；农历传 calendar=lunar。可提前 1-30 天提醒。", true,
                    text("name", 100, true), integer("month", 12, true), integer("day", 31, true),
                    choice("calendar", false, "solar", "lunar"), flag("recurring"),
                    integer("remindDaysBefore", 30, false), text("remindTime", 5, false)),
            spec("create_recipe", "新建菜谱。用料与步骤按行填写，一行一项。", true,
                    text("name", 100, true), choice("category", false, "HOT", "HARD", "COLD", "STAPLE", "PORRIDGE", "DESSERT"),
                    choice("cuisine", false, "CHUAN", "YUE", "LU", "SU", "ZHE", "MIN", "XIANG", "HUI", "OTHER"),
                    choice("flavor", false, "SAVORY", "SPICY", "SWEET_SOUR", "LIGHT", "OTHER"),
                    text("description", 500, false), text("ingredients", 2000, false),
                    text("equipment", 1000, false), text("steps", 4000, false)),
            spec("task_action", "推进家庭悬赏任务：领取 CLAIM、放弃 ABANDON、申报完成 FINISH、发布者确认 CONFIRM。task 用任务标题。", true,
                    text("task", 100, true), choice("action", true, "CLAIM", "ABANDON", "FINISH", "CONFIRM")),
            spec("update_reading_status", "更新本人对某本书的阅读状态：想读 WANT_READ、在读 READING、读完 FINISHED。book 用书名。", true,
                    text("book", 150, true), choice("status", true, "WANT_READ", "READING", "FINISHED")),
            spec("iot_control", "开关家庭智能设备（灯、开关、风扇、加湿器等）。device 用设备名称，名称重复时补 room。", true,
                    text("device", 100, true), choice("action", true, "ON", "OFF", "TOGGLE"), text("room", 50, false)),
            spec("redeem_points", "用本人积分兑换家庭商品。product 用商品名称；先查积分与商品再确认。", true,
                    text("product", 100, true)),
            spec("check_in", "本人今日签到领积分。", true),
            spec("take_item", "从家庭库存取出一件物品或食材并扣减数量。name 用物品名称，amount 为取出数量。", true,
                    text("name", 100, true), money()));

    private static AssistantToolSpec spec(String name, String description, boolean write, Field... fields) {
        return new AssistantToolSpec(name, description, write, List.of(fields));
    }
}
