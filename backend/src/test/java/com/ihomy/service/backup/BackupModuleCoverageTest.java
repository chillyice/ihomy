package com.ihomy.service.backup;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 守护模块 ↔ 表清单:新增表后必须归入某个模块(或加入永不备份清单),
 * 否则策略备份会悄悄漏表。以 schema.sql 为准,避免手工维护的清单漂移。
 */
class BackupModuleCoverageTest {

    private static final Pattern CREATE_TABLE = Pattern.compile("^CREATE TABLE `([a-z0-9_]+)`");

    @Test
    void everySchemaTableBelongsToAModule() throws Exception {
        Set<String> tables = schemaTables();
        assertThat(tables).isNotEmpty();

        Set<String> covered = BackupModule.allTables();
        Set<String> missing = new LinkedHashSet<>(tables);
        missing.removeAll(covered);
        missing.removeIf(BackupTableSpec::isNeverDump);

        assertThat(missing)
                .as("schema.sql 中存在未归入备份模块的表;请加入 BackupModule,或确认它属于永不备份")
                .isEmpty();
    }

    @Test
    void modulesDoNotReferenceUnknownTables() throws Exception {
        Set<String> tables = schemaTables();
        Set<String> unknown = new LinkedHashSet<>(BackupModule.allTables());
        unknown.removeAll(tables);
        assertThat(unknown)
                .as("BackupModule 引用了 schema.sql 中不存在的表,请同步删除或改名")
                .isEmpty();
    }

    @Test
    void policyTableIsBackedUpButJobTableIsNot() {
        assertThat(BackupModule.allTables()).contains("sys_backup_policy");
        assertThat(BackupTableSpec.isNeverDump("sys_backup_job")).isTrue();
        assertThat(BackupTableSpec.isNeverDump("sys_password_reset_token")).isTrue();
        assertThat(BackupModule.tablesFor(java.util.List.of("CONTENT"))).contains("content_diary", "content_photo");
        assertThat(BackupModule.includesFiles(java.util.List.of("CONTENT"))).isFalse();
        assertThat(BackupModule.includesFiles(java.util.List.of("CONTENT", "FILES"))).isTrue();
    }

    private Set<String> schemaTables() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/schema.sql")) {
            assertThat(in).as("schema.sql 必须在测试 classpath 上").isNotNull();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            Set<String> tables = new LinkedHashSet<>();
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher m = CREATE_TABLE.matcher(line);
                if (m.find()) tables.add(m.group(1));
            }
            return tables;
        }
    }
}
