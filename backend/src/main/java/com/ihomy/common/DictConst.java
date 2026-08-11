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
    public static final String ROLE_DISABLED = "DISABLED";
    private static final String ROLE_FALLBACK = ROLE_ENABLED;

    /** 愿望状态 wish_status / video_wish_status */
    public static final String WISH_PENDING = "PENDING";
    public static final String WISH_ACHIEVED = "ACHIEVED";
    public static final String WISH_ABANDONED = "ABANDONED";
    public static final String VWISH_PENDING = "PENDING";
    public static final String VWISH_IMPORTED = "IMPORTED";
    private static final String WISH_FALLBACK = WISH_PENDING;
    private static final String VWISH_FALLBACK = VWISH_PENDING;

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
    private static final String TASK_FALLBACK = TASK_OPEN;

    /** 任务奖励类型 reward_type */
    public static final String REWARD_NONE = "NONE";
    public static final String REWARD_POINTS = "POINTS";
    public static final String REWARD_ITEM = "ITEM";
    private static final String REWARD_FALLBACK = REWARD_NONE;

    /** 入家申请 apply_status */
    public static final String APPLY_PENDING = "PENDING";
    public static final String APPLY_APPROVED = "APPROVED";
    public static final String APPLY_REJECTED = "REJECTED";
    private static final String APPLY_FALLBACK = APPLY_PENDING;

    /** 积分订单 order_status */
    public static final String ORDER_PENDING = "PENDING";
    public static final String ORDER_REDEEMED = "REDEEMED";
    private static final String ORDER_FALLBACK = ORDER_PENDING;

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

    /** 操作日志结果 log_result */
    public static final String LOG_SUCCESS = "SUCCESS";
    public static final String LOG_FAILED = "FAILED";

    /** 邀请码 invite_status */
    public static final String INVITE_UNUSED = "UNUSED";
    public static final String INVITE_USED = "USED";
    private static final String INVITE_FALLBACK = INVITE_UNUSED;

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

    public static String userStatus(Integer v) {
        return v != null && v == 1 ? USER_DISABLED : USER_FALLBACK;
    }

    public static String roleStatus(Integer v) {
        return v != null && v == 0 ? ROLE_DISABLED : ROLE_FALLBACK;
    }

    public static String wishStatus(Integer v) {
        if (v == null) return WISH_FALLBACK;
        return switch (v) {
            case 1 -> WISH_ACHIEVED;
            case 2 -> WISH_ABANDONED;
            default -> WISH_PENDING;
        };
    }

    /** 放映厅想看映射是独立枚举(不同字典组),复用单词 PENDING/IMPORTED */
    public static String vwishStatus(Integer v) {
        return v != null && v == 1 ? VWISH_IMPORTED : VWISH_FALLBACK;
    }

    public static String planStatus(Integer v) {
        if (v == null) return PLAN_FALLBACK;
        return switch (v) {
            case 1 -> PLAN_DONE;
            case 2 -> PLAN_CANCELLED;
            default -> PLAN_ACTIVE;
        };
    }

    public static String taskStatus(Integer v) {
        if (v == null) return TASK_FALLBACK;
        return switch (v) {
            case 1 -> TASK_IN_PROGRESS;
            case 2 -> TASK_REVIEW;
            case 3 -> TASK_DONE;
            case 4 -> TASK_CANCELLED;
            default -> TASK_OPEN;
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

    public static String applyStatus(Integer v) {
        if (v == null) return APPLY_FALLBACK;
        return switch (v) {
            case 1 -> APPLY_APPROVED;
            case 2 -> APPLY_REJECTED;
            default -> APPLY_PENDING;
        };
    }

    public static String orderStatus(Integer v) {
        return v != null && v == 1 ? ORDER_REDEEMED : ORDER_FALLBACK;
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

    public static String inviteStatus(Integer v) {
        return v != null && v == 1 ? INVITE_USED : INVITE_FALLBACK;
    }
}