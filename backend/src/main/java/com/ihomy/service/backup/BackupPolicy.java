package com.ihomy.service.backup;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.scheduling.support.CronExpression;

import java.util.ArrayList;
import java.util.List;

/**
 * 定时备份策略(存 sys_parameter.name = backup.policy 的 JSON)。
 *
 * <p>默认口径:</p>
 * <ul>
 *   <li>基础数据:每天 02:30;保留 7 个日份 + 4 个周份 + 3 个月份</li>
 *   <li>内容数据:每天 03:30(文件按硬链接做增量快照);保留同上</li>
 *   <li>全量快照:每周日 04:30;保留 8 个周份 + 12 个月份</li>
 *   <li>单家庭导出:保留 30 天、最多 3 份</li>
 *   <li>迁移包:只手动生成,不自动清理</li>
 * </ul>
 *
 * <p>cron 采用 Spring 六段式(秒 分 时 日 月 周),时区取服务器本地时区。</p>
 */
@Data
@NoArgsConstructor
public class BackupPolicy {

    /** 总开关:关闭后不再触发任何定时任务(手动备份不受影响) */
    private boolean enabled = true;
    private Task basic = new Task(true, "0 30 2 * * *");
    private Task content = new Task(true, "0 30 3 * * *");
    private Task full = new Task(true, "0 30 4 * * 0");
    private Retention retention = new Retention();

    @Data
    @NoArgsConstructor
    public static class Task {
        private boolean enabled = true;
        private String cron;

        public Task(boolean enabled, String cron) {
            this.enabled = enabled;
            this.cron = cron;
        }
    }

    @Data
    @NoArgsConstructor
    public static class Retention {
        /** 基础数据:每天保留几份(按日取最新) */
        private int basicDaily = 7;
        /** 基础数据:每周保留几份(按 ISO 周取最新) */
        private int basicWeekly = 4;
        /** 基础数据:每月保留几份 */
        private int basicMonthly = 3;

        private int contentDaily = 7;
        private int contentWeekly = 4;
        private int contentMonthly = 3;

        /** 全量快照:每周保留几份 */
        private int fullWeekly = 8;
        /** 全量快照:每月保留几份 */
        private int fullMonthly = 12;

        /** 单家庭导出:超过多少天后可清理 */
        private int familyDays = 30;
        /** 单家庭导出:每个家庭最多保留几份 */
        private int familyKeep = 3;
    }

    /** 缺字段时补默认值(兼容旧版 JSON / 前端只提交部分字段) */
    public BackupPolicy normalized() {
        if (basic == null) basic = new Task(true, "0 30 2 * * *");
        if (content == null) content = new Task(true, "0 30 3 * * *");
        if (full == null) full = new Task(true, "0 30 4 * * 0");
        if (retention == null) retention = new Retention();
        if (basic.getCron() == null || basic.getCron().isBlank()) basic.setCron("0 30 2 * * *");
        if (content.getCron() == null || content.getCron().isBlank()) content.setCron("0 30 3 * * *");
        if (full.getCron() == null || full.getCron().isBlank()) full.setCron("0 30 4 * * 0");
        return this;
    }

    /** 返回全部校验错误;空列表表示可用 */
    public List<String> validate() {
        BackupPolicy p = normalized();
        List<String> errors = new ArrayList<>();
        checkCron("基础数据", p.getBasic(), errors);
        checkCron("内容数据", p.getContent(), errors);
        checkCron("全量快照", p.getFull(), errors);

        Retention r = p.getRetention();
        range("基础数据日保留", r.getBasicDaily(), 1, 90, errors);
        range("基础数据周保留", r.getBasicWeekly(), 0, 52, errors);
        range("基础数据月保留", r.getBasicMonthly(), 0, 24, errors);
        range("内容数据日保留", r.getContentDaily(), 1, 90, errors);
        range("内容数据周保留", r.getContentWeekly(), 0, 52, errors);
        range("内容数据月保留", r.getContentMonthly(), 0, 24, errors);
        range("全量周保留", r.getFullWeekly(), 1, 52, errors);
        range("全量月保留", r.getFullMonthly(), 0, 36, errors);
        range("家庭导出保留天数", r.getFamilyDays(), 1, 365, errors);
        range("家庭导出保留份数", r.getFamilyKeep(), 1, 20, errors);
        return errors;
    }

    private static void checkCron(String label, Task task, List<String> errors) {
        String cron = task.getCron();
        if (cron == null || cron.isBlank()) {
            errors.add(label + "的执行周期不能为空");
            return;
        }
        try {
            CronExpression.parse(cron.trim());
        } catch (IllegalArgumentException e) {
            errors.add(label + "的执行周期格式不正确(需 6 段:cron 秒 分 时 日 月 周)");
        }
    }

    private static void range(String label, int value, int min, int max, List<String> errors) {
        if (value < min || value > max) {
            errors.add(label + "需在 " + min + "–" + max + " 之间");
        }
    }
}
