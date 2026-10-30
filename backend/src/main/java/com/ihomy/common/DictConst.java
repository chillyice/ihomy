package com.ihomy.common;

/**
 * 数据字典常量(V3.9):所有业务状态/类型字段在 DB 中以大写英文单词存储,
 * 与 sys_dict_item 表的 dict_value 一一对应。禁止散落魔法字符串。
 */
public final class DictConst {

    private DictConst() {
    }

    /** 可见范围 visibility */
    public static final String VIS_PRIVATE = "PRIVATE";
    public static final String VIS_MEMBERS = "MEMBERS";
    public static final String VIS_GROUPS = "GROUPS";
    public static final String VIS_FAMILY = "FAMILY";
    public static final String VIS_PUBLIC = "PUBLIC";
    private static final String VIS_FALLBACK = VIS_FAMILY;

    /** 博客状态 blog.status */
    public static final String BLOG_DRAFT = "DRAFT";
    public static final String BLOG_PUBLISHED = "PUBLISHED";
    public static final String BLOG_HIDDEN = "HIDDEN";
    private static final String BLOG_FALLBACK = BLOG_DRAFT;

    /** 用户状态 user_status */
    public static final String USER_ACTIVE = "ACTIVE";
    public static final String USER_DISABLED = "DISABLED";
    private static final String USER_FALLBACK = USER_ACTIVE;

    /** 角色状态 role_status */
    public static final String ROLE_ENABLED = "ENABLED";
    private static final String ROLE_FALLBACK = ROLE_ENABLED;

    /** 愿望状态 wish_status / video_wish_status */
    public static final String WISH_PENDING = "PENDING";
    public static final String WISH_ACHIEVED = "ACHIEVED";
    public static final String WISH_ABANDONED = "ABANDONED";
    public static final String VWISH_PENDING = "PENDING";
    public static final String VWISH_IMPORTED = "IMPORTED";
    private static final String WISH_FALLBACK = WISH_PENDING;

    /** 愿望来源 wish_source(V10.92):空=直接添加,非空=从放映厅/纪念日/小游戏/家庭计划许愿 */
    public static final String WISH_SOURCE_CINEMA = "CINEMA";
    public static final String WISH_SOURCE_ANNIVERSARY = "ANNIVERSARY";
    public static final String WISH_SOURCE_GAME = "GAME";
    public static final String WISH_SOURCE_PLAN = "PLAN";

    /** 放映厅媒体引擎类型 media_server_type */
    public static final String MEDIA_JELLYFIN = "JELLYFIN";
    public static final String MEDIA_EMBY = "EMBY";
    private static final String MEDIA_FALLBACK = MEDIA_JELLYFIN;

    /** 法定节假日/调休类型 holiday_type(V10.93):sys_holiday.type */
    public static final String HOLIDAY_TYPE_HOLIDAY = "HOLIDAY";
    public static final String HOLIDAY_TYPE_WORKDAY = "WORKDAY";

    /** 家庭计划状态 plan_status */
    public static final String PLAN_ACTIVE = "ACTIVE";
    public static final String PLAN_DONE = "DONE";
    public static final String PLAN_CANCELLED = "CANCELLED";
    private static final String PLAN_FALLBACK = PLAN_ACTIVE;

    /** 任务状态 task_status */
    public static final String TASK_OPEN = "OPEN";
    public static final String TASK_IN_PROGRESS = "IN_PROGRESS";
    public static final String TASK_REVIEW = "REVIEW";
    public static final String TASK_DONE = "DONE";
    public static final String TASK_CANCELLED = "CANCELLED";

    /** 任务奖励类型 reward_type */
    public static final String REWARD_NONE = "NONE";
    public static final String REWARD_POINTS = "POINTS";
    public static final String REWARD_ITEM = "ITEM";
    private static final String REWARD_FALLBACK = REWARD_NONE;

    /** 入家申请 apply_status */
    public static final String APPLY_PENDING = "PENDING";
    public static final String APPLY_APPROVED = "APPROVED";
    public static final String APPLY_REJECTED = "REJECTED";

    /** 积分订单 order_status */
    public static final String ORDER_PENDING = "PENDING";
    public static final String ORDER_REDEEMED = "REDEEMED";

    /** 提醒周期 reminder_repeat */
    public static final String REPEAT_ONCE = "ONCE";
    public static final String REPEAT_DAILY = "DAILY";
    public static final String REPEAT_WEEKLY = "WEEKLY";
    public static final String REPEAT_MONTHLY = "MONTHLY";
    private static final String REPEAT_FALLBACK = REPEAT_ONCE;

    /** 纪念日周期 ann_recurring */
    public static final String RECUR_ONCE = "ONCE";
    public static final String RECUR_YEARLY = "YEARLY";
    private static final String RECUR_FALLBACK = RECUR_YEARLY;

    /** 记账类型 book_type */
    public static final String BOOK_EXPENSE = "EXPENSE";
    public static final String BOOK_INCOME = "INCOME";
    public static final String BOOK_TRANSFER = "TRANSFER";
    private static final String BOOK_FALLBACK = BOOK_EXPENSE;

    /** 记账账户类型 book_account_type（V10.78） */
    public static final String BOOK_ACCOUNT_CASH = "CASH";
    public static final String BOOK_ACCOUNT_BANK = "BANK";
    public static final String BOOK_ACCOUNT_ALIPAY = "ALIPAY";
    public static final String BOOK_ACCOUNT_WECHAT = "WECHAT";
    public static final String BOOK_ACCOUNT_CREDIT = "CREDIT";
    public static final String BOOK_ACCOUNT_OTHER = "OTHER";
    private static final String BOOK_ACCOUNT_FALLBACK = BOOK_ACCOUNT_BANK;

    /** 理财类型 finance_type（V10.80） */
    public static final String FINANCE_TYPE_DEPOSIT = "DEPOSIT";
    public static final String FINANCE_TYPE_LARGE_DEPOSIT = "LARGE_DEPOSIT";
    public static final String FINANCE_TYPE_BOND = "BOND";
    public static final String FINANCE_TYPE_BANK_WMP = "BANK_WMP";
    public static final String FINANCE_TYPE_FUND = "FUND";
    public static final String FINANCE_TYPE_INSURANCE = "INSURANCE";
    public static final String FINANCE_TYPE_OTHER = "OTHER";
    private static final String FINANCE_TYPE_FALLBACK = FINANCE_TYPE_DEPOSIT;

    /** 理财状态 finance_status（V10.80）；MATURED 由到期日派生，库内一般不主动写 */
    public static final String FINANCE_HOLDING = "HOLDING";
    public static final String FINANCE_MATURED = "MATURED";
    public static final String FINANCE_REDEEMED = "REDEEMED";
    public static final String FINANCE_CANCELLED = "CANCELLED";
    private static final String FINANCE_STATUS_FALLBACK = FINANCE_HOLDING;

    /** 理财计息方式 finance_interest_mode（V10.80） */
    public static final String FINANCE_MODE_BY_MONTH = "BY_MONTH";
    public static final String FINANCE_MODE_BY_DAY = "BY_DAY";
    public static final String FINANCE_MODE_COMPOUND = "COMPOUND";
    private static final String FINANCE_MODE_FALLBACK = FINANCE_MODE_BY_MONTH;

    /** 操作日志结果 log_result */
    public static final String LOG_SUCCESS = "SUCCESS";
    public static final String LOG_FAILED = "FAILED";

    /** 邀请码 invite_status */
    public static final String INVITE_UNUSED = "UNUSED";
    private static final String INVITE_FALLBACK = INVITE_UNUSED;

    /** 菜谱类别 recipe_category */
    public static final String RECIPE_HOT = "HOT";
    public static final String RECIPE_HARD = "HARD";
    public static final String RECIPE_COLD = "COLD";
    public static final String RECIPE_STAPLE = "STAPLE";
    public static final String RECIPE_PORRIDGE = "PORRIDGE";
    public static final String RECIPE_DESSERT = "DESSERT";

    /** 物品类型 item_type */
    public static final String ITEM_KITCHENWARE = "KITCHENWARE";
    public static final String ITEM_INGREDIENT = "INGREDIENT";
    public static final String ITEM_DAILY = "DAILY";
    public static final String ITEM_CLOTHES = "CLOTHES";
    public static final String ITEM_TOOL = "TOOL";
    public static final String ITEM_OTHER = "OTHER";

    /** 电子书格式 book_format */
    public static final String FMT_EPUB = "EPUB";
    public static final String FMT_PDF = "PDF";
    public static final String FMT_TXT = "TXT";
    public static final String FMT_MOBI = "MOBI";

    /** 阅读状态 borrow_status */
    public static final String BORROW_WANT = "WANT_READ";
    public static final String BORROW_READING = "READING";
    public static final String BORROW_FINISHED = "FINISHED";

    /** 开源组件类型 oss_component_type */
    public static final String OSS_TYPE_NPM = "NPM";
    public static final String OSS_TYPE_MAVEN = "MAVEN";
    public static final String OSS_TYPE_SERVICE = "SERVICE";

    /** 开源组件更新类型 oss_update_type */
    public static final String OSS_UPDATE_MAJOR = "MAJOR";
    public static final String OSS_UPDATE_MINOR = "MINOR";
    public static final String OSS_UPDATE_PATCH = "PATCH";
    public static final String OSS_UPDATE_NONE = "NONE";

    /** 开源组件状态 oss_status */
    public static final String OSS_ACTIVE = "ACTIVE";
    public static final String OSS_IGNORED = "IGNORED";

    /** 开源组件集成状态 oss_integration */
    public static final String OSS_INTEG_FULL = "FULL";
    public static final String OSS_INTEG_PARTIAL = "PARTIAL";
    public static final String OSS_INTEG_PLANNED = "PLANNED";

    /** 开源组件管理方 oss_managed_by:RENOVATE 由 Renovate 检测+生成 PR / INTERNAL 台账内部维护 */
    public static final String OSS_MANAGED_RENOVATE = "RENOVATE";
    public static final String OSS_MANAGED_INTERNAL = "INTERNAL";

    /** 独立服务部署方式 oss_deploy_type(SERVICE 专用) */
    public static final String OSS_DEPLOY_CONTAINER = "CONTAINER";
    public static final String OSS_DEPLOY_SYSTEMD = "SYSTEMD";
    public static final String OSS_DEPLOY_OTHER = "OTHER";

    /** 独立服务当前版本探测方式 oss_probe_type(SERVICE 专用;空=管理员手工维护当前版本) */
    public static final String OSS_PROBE_NEXTCLOUD = "NEXTCLOUD_STATUS";
    public static final String OSS_PROBE_JELLYFIN = "JELLYFIN_INFO";
    public static final String OSS_PROBE_HA = "HA_CONFIG";

    /* ---------------- 整数(历史 DTO 入参)→ 字典词 转换 ---------------- */

    public static String visibility(Integer v) {
        if (v == null) return VIS_FALLBACK;
        return switch (v) {
            case 0 -> VIS_PRIVATE;
            case 1 -> VIS_MEMBERS;
            case 2 -> VIS_GROUPS;
            case 4 -> VIS_PUBLIC;
            default -> VIS_FAMILY;
        };
    }

    public static String blogStatus(Integer v) {
        if (v == null) return BLOG_FALLBACK;
        return switch (v) {
            case 1 -> BLOG_PUBLISHED;
            case 2 -> BLOG_HIDDEN;
            default -> BLOG_DRAFT;
        };
    }

    public static String wishStatus(Integer v) {
        if (v == null) return WISH_FALLBACK;
        return switch (v) {
            case 1 -> WISH_ACHIEVED;
            case 2 -> WISH_ABANDONED;
            default -> WISH_PENDING;
        };
    }

    public static String planStatus(Integer v) {
        if (v == null) return PLAN_FALLBACK;
        return switch (v) {
            case 1 -> PLAN_DONE;
            case 2 -> PLAN_CANCELLED;
            default -> PLAN_ACTIVE;
        };
    }

    public static String rewardType(Integer v) {
        if (v == null) return REWARD_FALLBACK;
        return switch (v) {
            case 1 -> REWARD_POINTS;
            case 2 -> REWARD_ITEM;
            default -> REWARD_NONE;
        };
    }

    public static String repeatType(Integer v) {
        if (v == null) return REPEAT_FALLBACK;
        return switch (v) {
            case 1 -> REPEAT_DAILY;
            case 2 -> REPEAT_WEEKLY;
            case 3 -> REPEAT_MONTHLY;
            default -> REPEAT_ONCE;
        };
    }

    public static String annRecurring(Integer v) {
        return v != null && v == 1 ? RECUR_YEARLY : RECUR_FALLBACK;
    }

    public static String bookType(Integer v) {
        if (v == null) return BOOK_FALLBACK;
        return switch (v) {
            case 1 -> BOOK_INCOME;
            case 2 -> BOOK_TRANSFER;
            default -> BOOK_EXPENSE;
        };
    }

    /** 放映厅引擎类型(字符串枚举,非历史整数):未知值一律按 Jellyfin 处理 */
    public static String mediaServerType(String v) {
        return v != null && MEDIA_EMBY.equalsIgnoreCase(v.trim()) ? MEDIA_EMBY : MEDIA_FALLBACK;
    }

    /* ---------------- 字符串(字典词)入参 → 规范化:未知值回退默认,兼容历史数字串 ---------------- */

    public static String visibility(String v) {
        if (v == null || v.isBlank()) return VIS_FALLBACK;
        return switch (v.trim().toUpperCase()) {
            case VIS_PRIVATE, "0" -> VIS_PRIVATE;
            case VIS_MEMBERS, "1" -> VIS_MEMBERS;
            case VIS_GROUPS, "2" -> VIS_GROUPS;
            case VIS_PUBLIC, "4" -> VIS_PUBLIC;
            default -> VIS_FAMILY;
        };
    }

    public static String blogStatus(String v) {
        if (v == null || v.isBlank()) return BLOG_FALLBACK;
        return switch (v.trim().toUpperCase()) {
            case BLOG_PUBLISHED, "1" -> BLOG_PUBLISHED;
            case BLOG_HIDDEN, "2" -> BLOG_HIDDEN;
            default -> BLOG_DRAFT;
        };
    }

    public static String wishStatus(String v) {
        if (v == null || v.isBlank()) return WISH_FALLBACK;
        return switch (v.trim().toUpperCase()) {
            case WISH_ACHIEVED, "1" -> WISH_ACHIEVED;
            case WISH_ABANDONED, "2" -> WISH_ABANDONED;
            default -> WISH_PENDING;
        };
    }

    public static String planStatus(String v) {
        if (v == null || v.isBlank()) return PLAN_FALLBACK;
        return switch (v.trim().toUpperCase()) {
            case PLAN_DONE, "1" -> PLAN_DONE;
            case PLAN_CANCELLED, "2" -> PLAN_CANCELLED;
            default -> PLAN_ACTIVE;
        };
    }

    /** 愿望来源入参规范化:空=直接添加返回 null;未知值也返回 null,由调用方判空后报错(不静默回退) */
    public static String wishSource(String v) {
        if (v == null || v.isBlank()) return null;
        return switch (v.trim().toUpperCase()) {
            case WISH_SOURCE_CINEMA -> WISH_SOURCE_CINEMA;
            case WISH_SOURCE_ANNIVERSARY -> WISH_SOURCE_ANNIVERSARY;
            case WISH_SOURCE_GAME -> WISH_SOURCE_GAME;
            case WISH_SOURCE_PLAN -> WISH_SOURCE_PLAN;
            default -> null;
        };
    }

    public static String rewardType(String v) {
        if (v == null || v.isBlank()) return REWARD_FALLBACK;
        return switch (v.trim().toUpperCase()) {
            case REWARD_POINTS, "1" -> REWARD_POINTS;
            case REWARD_ITEM, "2" -> REWARD_ITEM;
            default -> REWARD_NONE;
        };
    }

    public static String repeatType(String v) {
        if (v == null || v.isBlank()) return REPEAT_FALLBACK;
        return switch (v.trim().toUpperCase()) {
            case REPEAT_DAILY, "1" -> REPEAT_DAILY;
            case REPEAT_WEEKLY, "2" -> REPEAT_WEEKLY;
            case REPEAT_MONTHLY, "3" -> REPEAT_MONTHLY;
            default -> REPEAT_ONCE;
        };
    }

    public static String bookType(String v) {
        if (v == null || v.isBlank()) return BOOK_FALLBACK;
        return switch (v.trim().toUpperCase()) {
            case BOOK_INCOME, "1" -> BOOK_INCOME;
            case BOOK_TRANSFER, "2" -> BOOK_TRANSFER;
            default -> BOOK_EXPENSE;
        };
    }

    /** 记账账户类型归一；未知值落 BANK，避免前端脏数据写入越界枚举 */
    public static String bookAccountType(String v) {
        if (v == null || v.isBlank()) return BOOK_ACCOUNT_FALLBACK;
        return switch (v.trim().toUpperCase()) {
            case BOOK_ACCOUNT_CASH -> BOOK_ACCOUNT_CASH;
            case BOOK_ACCOUNT_ALIPAY -> BOOK_ACCOUNT_ALIPAY;
            case BOOK_ACCOUNT_WECHAT -> BOOK_ACCOUNT_WECHAT;
            case BOOK_ACCOUNT_CREDIT -> BOOK_ACCOUNT_CREDIT;
            case BOOK_ACCOUNT_OTHER -> BOOK_ACCOUNT_OTHER;
            default -> BOOK_ACCOUNT_BANK;
        };
    }

    /** 理财类型归一：未知值落「定期存款」 */
    public static String financeType(String v) {
        if (v == null || v.isBlank()) return FINANCE_TYPE_FALLBACK;
        return switch (v.trim().toUpperCase()) {
            case FINANCE_TYPE_LARGE_DEPOSIT -> FINANCE_TYPE_LARGE_DEPOSIT;
            case FINANCE_TYPE_BOND -> FINANCE_TYPE_BOND;
            case FINANCE_TYPE_BANK_WMP -> FINANCE_TYPE_BANK_WMP;
            case FINANCE_TYPE_FUND -> FINANCE_TYPE_FUND;
            case FINANCE_TYPE_INSURANCE -> FINANCE_TYPE_INSURANCE;
            case FINANCE_TYPE_OTHER -> FINANCE_TYPE_OTHER;
            default -> FINANCE_TYPE_DEPOSIT;
        };
    }

    /** 理财状态归一：未知值落「持有中」（到期与否以到期日派生，不靠库内状态） */
    public static String financeStatus(String v) {
        if (v == null || v.isBlank()) return FINANCE_STATUS_FALLBACK;
        return switch (v.trim().toUpperCase()) {
            case FINANCE_MATURED -> FINANCE_MATURED;
            case FINANCE_REDEEMED -> FINANCE_REDEEMED;
            case FINANCE_CANCELLED -> FINANCE_CANCELLED;
            default -> FINANCE_HOLDING;
        };
    }

    /** 理财计息方式归一：未知值落「按月折算」 */
    public static String financeMode(String v) {
        if (v == null || v.isBlank()) return FINANCE_MODE_FALLBACK;
        return switch (v.trim().toUpperCase()) {
            case FINANCE_MODE_BY_DAY -> FINANCE_MODE_BY_DAY;
            case FINANCE_MODE_COMPOUND -> FINANCE_MODE_COMPOUND;
            default -> FINANCE_MODE_BY_MONTH;
        };
    }

    /** 计息基准天数：只认 360/365，其余按 365（与前端 utils/finance.js 同口径） */
    public static int financeDayCount(Integer v) {
        return v != null && v == 360 ? 360 : 365;
    }
}