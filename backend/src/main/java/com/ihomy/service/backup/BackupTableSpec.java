package com.ihomy.service.backup;

import java.util.Set;

/**
 * 备份表分类与单家庭导出的过滤口径(纯规则,便于单测)。
 *
 * <p>实例备份按前缀分两档:</p>
 * <ul>
 *   <li>基础数据 BASIC:sys_* + family_*(账号/权限/家庭/日程/计划/提醒/记账/物品/家谱/保险箱等)</li>
 *   <li>内容数据 CONTENT:content_* + report_* + game_*(博客/日记/相册/视频/音乐/图书/脑图/评论点赞 + 统计报表 + 游戏库)</li>
 * </ul>
 *
 * <p>两类运维记录不进任何备份:备份任务自身(sys_backup_job)与密码重置一次性令牌(sys_password_reset_token)。</p>
 */
public final class BackupTableSpec {

    /** 永不进入备份的表:运维产物 / 一次性敏感令牌 */
    private static final Set<String> NEVER_DUMP = Set.of(
            "sys_backup_job",
            "sys_password_reset_token"
    );

    /** 单家庭导出时跳过的全局种子表(目标实例装库时已有;重复导入反而冲突) */
    private static final Set<String> FAMILY_GLOBAL_SKIP = Set.of(
            "sys_role",
            "sys_auth",
            "sys_role_auth",
            "sys_home_module",
            "sys_dict_item",
            "sys_holiday",
            "sys_parameter",
            "sys_synonym",
            "sys_oss_component",
            "sys_kada_snippet",
            "report_system"
    );

    private BackupTableSpec() {
    }

    /** 内容数据档:content_/report_/game_ 前缀 */
    public static boolean isContentTable(String table) {
        if (table == null) return false;
        return table.startsWith("content_") || table.startsWith("report_") || table.startsWith("game_");
    }

    /** 某类备份是否包含该表 */
    public static boolean includeInDump(String table, BackupKind kind) {
        if (table == null || kind == null) return false;
        if (NEVER_DUMP.contains(table)) return false;
        if (kind == BackupKind.BASIC) return !isContentTable(table);
        if (kind == BackupKind.CONTENT) return isContentTable(table);
        return true; // FULL / MIGRATION
    }

    /**
     * 单家庭导出的 WHERE 条件。
     *
     * @param columns 该表实际列名(小写)
     * @return null 表示该表不参与家庭导出;否则返回 where 片段与参数个数
     */
    public static FamilyFilter familyFilter(String table, Set<String> columns) {
        if (table == null || columns == null) return null;
        if (NEVER_DUMP.contains(table) || FAMILY_GLOBAL_SKIP.contains(table)) return null;
        if (Set.of("sys_ai_conversation", "sys_ai_message", "sys_ai_action").contains(table)) return null;
        if ("content_diary".equals(table)) return new FamilyFilter("`family_id` = ? AND `visibility` <> 'PRIVATE'", 1);
        if ("content_comment".equals(table)) return new FamilyFilter(
                "`family_id` = ? AND (`content_type` <> 'diary' OR NOT EXISTS "
                        + "(SELECT 1 FROM content_diary d WHERE d.id = content_comment.content_id AND d.visibility = 'PRIVATE'))", 1);
        if ("sys_family_info".equals(table)) return new FamilyFilter("`id` = ?", 1);
        if ("sys_user_pref".equals(table)) {
            return new FamilyFilter("`user_id` IN (SELECT `id` FROM `sys_user` WHERE `family_id` = ?)", 1);
        }
        if ("sys_user_group_member".equals(table)) {
            return new FamilyFilter("`group_id` IN (SELECT `id` FROM `sys_user_group` WHERE `family_id` = ?)", 1);
        }
        if ("sys_chat_dm_message".equals(table)) {
            return new FamilyFilter(
                    "(`sender_id` IN (SELECT `id` FROM `sys_user` WHERE `family_id` = ?)"
                            + " OR `recipient_id` IN (SELECT `id` FROM `sys_user` WHERE `family_id` = ?))", 2);
        }
        if (columns.contains("family_id")) return new FamilyFilter("`family_id` = ?", 1);
        return null;
    }

    public record FamilyFilter(String where, int params) {
    }
}
