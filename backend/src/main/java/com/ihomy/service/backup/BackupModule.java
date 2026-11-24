package com.ihomy.service.backup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 备份模块:一条备份策略勾选哪些模块,就导出这些模块对应的表;
 * 勾选「上传文件」时,额外对 uploads 做硬链接增量快照。
 *
 * <p>模块与表是显式映射(不按前缀猜),新增表时必须归入某个模块;
 * {@code BackupModuleCoverageTest} 会用 schema.sql 校验没有漏登记的表。</p>
 */
public enum BackupModule {

    /** 家庭成员与权限:账号、角色、家庭、群组、亲友圈 */
    MEMBER(Set.of(
            "sys_user", "sys_role", "sys_auth", "sys_user_role", "sys_role_auth", "sys_family_info",
            "sys_user_group", "sys_user_group_member", "sys_user_pref",
            "family_user_label", "family_tree", "family_invitation_code", "family_apply",
            "family_circle", "family_circle_member", "family_share_default"
    )),

    /** 家庭事务:家务、种植、家居、物品、菜谱、保险箱、公告、签到积分、通知 */
    LIFE(Set.of(
            "family_task", "family_plant", "family_plant_log", "family_house", "family_room",
            "family_furniture", "family_item", "family_recipe", "family_vault_item",
            "family_announcement", "family_file", "family_checkin",
            "family_points_record", "family_points_product", "family_points_order", "family_points_rule",
            "family_notification"
    )),

    /** 日程与计划:计划、提醒、纪念日 */
    SCHEDULE(Set.of(
            "family_plan", "family_plan_task", "family_reminder", "family_reminder_check",
            "family_anniversary", "family_anniversary_member"
    )),

    /** 记账与贷款 */
    BOOK(Set.of(
            "family_book_record", "family_book_account", "family_book_budget", "family_book_recurring",
            "family_book_permission", "family_loan", "family_loan_event", "family_loan_repay",
            "family_finance_product"
    )),

    /** 内容:博客、日记、相册、视频、音乐、图书、愿望、脑图、评论点赞、分享受众 */
    CONTENT(Set.of(
            "content_blog", "content_blog_category", "content_diary", "content_photo_album", "content_photo",
            "content_video", "content_video_wish", "content_comment", "content_visibility", "content_like",
            "content_wish", "content_music", "content_music_playlist", "content_music_playlist_track",
            "content_book", "content_book_borrow", "content_book_category", "content_book_category_rel",
            "content_book_bookmark", "content_mindmap", "content_mindmap_snapshot", "content_audience"
    )),

    /** 聊天记录:家庭聊天与私聊消息 */
    CHAT(Set.of("family_chat_message", "family_chat_read", "sys_chat_dm_message")),

    /** 健康数据 */
    HEALTH(Set.of(
            "family_health_sleep", "family_health_sleep_stage", "family_health_sample", "family_health_workout",
            "family_health_view_log", "family_health_share", "sys_health_token"
    )),

    /** 游戏:游戏库、房间与对局 */
    GAME(Set.of(
            "game_info", "family_game_match", "family_game_room", "family_game_room_member",
            "family_game_room_round"
    )),

    /** 统计报表 */
    REPORT(Set.of(
            "report_system", "report_weather", "report_ai", "report_alert", "report_product_stat"
    )),

    /** 系统配置:首页模块、字典、参数、存储与媒体、天气、AI、智能家居、开源台账、助手会话 */
    CONFIG(Set.of(
            "sys_home_module", "sys_storage_device", "sys_baidu_credential", "sys_kada_snippet",
            "sys_dict_item", "sys_weather_credential", "sys_parameter", "sys_weather_location",
            "sys_family_ai_model", "sys_family_ai_feature", "sys_synonym", "sys_oss_component",
            "sys_media_server", "sys_media_user_config", "sys_iot_config", "sys_iot_device", "sys_iot_data",
            "sys_holiday", "sys_ai_conversation", "sys_ai_message", "sys_ai_action",
            "sys_backup_policy"
    )),

    /** 上传文件:uploads 目录下的照片/视频/图书等实体文件,不对应表 */
    FILES(Set.of(), true);

    private final Set<String> tables;
    private final boolean withFiles;

    BackupModule(Set<String> tables) {
        this(tables, false);
    }

    BackupModule(Set<String> tables, boolean withFiles) {
        this.tables = tables;
        this.withFiles = withFiles;
    }

    public Set<String> tables() {
        return tables;
    }

    public boolean withFiles() {
        return withFiles;
    }

    /** 所有模块覆盖的表(不含 FILES) */
    public static Set<String> allTables() {
        Set<String> out = new LinkedHashSet<>();
        for (BackupModule module : values()) out.addAll(module.tables);
        return out;
    }

    /** 按模块码取表集合;未知码忽略(已由策略校验拦截) */
    public static Set<String> tablesFor(Collection<String> codes) {
        Set<String> out = new LinkedHashSet<>();
        for (String code : codes) {
            BackupModule module = find(code);
            if (module != null) out.addAll(module.tables);
        }
        return out;
    }

    public static boolean includesFiles(Collection<String> codes) {
        for (String code : codes) {
            BackupModule module = find(code);
            if (module != null && module.withFiles) return true;
        }
        return false;
    }

    public static BackupModule find(String code) {
        if (code == null || code.isBlank()) return null;
        try {
            return valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static List<String> parse(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (String part : Arrays.asList(csv.split(","))) {
            if (!part.isBlank()) out.add(part.trim().toUpperCase(Locale.ROOT));
        }
        return out;
    }

    public static String csv(Collection<String> codes) {
        return String.join(",", codes);
    }

    /** 按枚举顺序整理并去重,便于入库与回显 */
    public static List<String> normalize(Collection<String> codes) {
        Set<String> set = new LinkedHashSet<>();
        for (BackupModule module : values()) {
            if (codes.stream().anyMatch(code -> module.name().equalsIgnoreCase(String.valueOf(code).trim()))) {
                set.add(module.name());
            }
        }
        return new ArrayList<>(set);
    }
}
