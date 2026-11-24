package com.ihomy.service.backup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

/**
 * 纯 JDBC 逻辑备份写入器:mysqldump 的等价子集。
 *
 * <p>为什么不用 mysqldump:生产后端是 systemd 里的 jar,宿主机不一定装 mysql 客户端,
 * 且应用账号也没有执行外部命令的职责。这里用 DataSource 直接流式读表,
 * 生成「DROP/CREATE + 批量 INSERT」的 .sql.gz,可用 mysql 客户端直接导入。</p>
 *
 * <p>内存:结果集 setFetchSize(Integer.MIN_VALUE) 走 MySQL 流式游标,
 * 逐行拼 SQL,每 500 行刷一次;不会把整表读进堆(生产 -Xmx384m)。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BackupDumpWriter {

    private static final int BATCH_ROWS = 500;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DataSource dataSource;

    public record DumpResult(
            long rows,
            long bytes,
            String sha256,
            int tableCount,
            Map<String, Long> tables,
            Set<String> fileRefs
    ) {
    }

    public DumpResult write(Path target, BackupKind kind, Long familyId) throws Exception {
        return write(target, kind, familyId, null);
    }

    /**
     * @param target     输出 .sql.gz
     * @param kind       备份类型(决定基础/内容/全量)
     * @param familyId   非空 = 单家庭导出(沿用全量分类,但按家庭过滤)
     * @param onlyTables 非空 = 只导出这些表(策略备份按模块显式指定)
     */
    public DumpResult write(Path target, BackupKind kind, Long familyId, Set<String> onlyTables) throws Exception {
        Files.createDirectories(target.getParent());
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        CountingOutputStream counting = new CountingOutputStream(Files.newOutputStream(target));
        Set<String> fileRefs = new LinkedHashSet<>();
        Map<String, Long> tableRows = new LinkedHashMap<>();
        long totalRows = 0;
        int tableCount = 0;

        try (Connection conn = dataSource.getConnection();
             OutputStream fileOut = counting;
             GZIPOutputStream gzip = new GZIPOutputStream(new BufferedOutputStream(fileOut, 1 << 16));
             DigestOutputStream digestOut = new DigestOutputStream(gzip, digest);
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(digestOut, StandardCharsets.UTF_8), 1 << 16)) {

            writer.write("-- ihomy backup\n");
            writer.write("-- generated: " + LocalDateTime.now().format(STAMP) + "\n");
            writer.write("-- kind: " + kind + (familyId == null ? " (instance)" : " (family " + familyId + ")") + "\n");
            writer.write("SET NAMES utf8mb4;\n");
            writer.write("SET FOREIGN_KEY_CHECKS=0;\n");
            writer.write("SET SQL_MODE='NO_AUTO_VALUE_ON_ZERO';\n\n");

            for (String table : listTables(conn)) {
                Set<String> columns = columns(conn, table);
                BackupTableSpec.FamilyFilter filter = null;
                if (BackupTableSpec.isNeverDump(table)) continue;
                if (familyId == null) {
                    if (onlyTables != null) {
                        if (!onlyTables.contains(table)) continue;
                    } else if (!BackupTableSpec.includeInDump(table, kind)) {
                        continue;
                    }
                } else {
                    filter = BackupTableSpec.familyFilter(table, columns);
                    if (filter == null) continue;
                }

                writeCreateTable(writer, conn, table);
                long rows = writeRows(writer, conn, table, filter, familyId, fileRefs);
                tableRows.put(table, rows);
                totalRows += rows;
                tableCount++;
                if (rows > 0) writer.write("\n");
                log.debug("backup dump table={} rows={}", table, rows);
            }

            writer.write("SET FOREIGN_KEY_CHECKS=1;\n");
            writer.flush();
        }

        return new DumpResult(totalRows, counting.count, BackupSql.hex(digest.digest()), tableCount, tableRows, fileRefs);
    }

    private List<String> listTables(Connection conn) throws SQLException {
        List<String> tables = new ArrayList<>();
        String sql = "SELECT table_name FROM information_schema.tables "
                + "WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE' ORDER BY table_name";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) tables.add(rs.getString(1));
        }
        return tables;
    }

    private Set<String> columns(Connection conn, String table) throws SQLException {
        Set<String> cols = new LinkedHashSet<>();
        String sql = "SELECT column_name FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) cols.add(rs.getString(1).toLowerCase(Locale.ROOT));
            }
        }
        return cols;
    }

    private void writeCreateTable(BufferedWriter writer, Connection conn, String table) throws SQLException, IOException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SHOW CREATE TABLE `" + table + "`")) {
            if (!rs.next()) return;
            String ddl = rs.getString(2);
            writer.write("-- -------- " + table + " --------\n");
            writer.write("DROP TABLE IF EXISTS `" + table + "`;\n");
            writer.write(ddl);
            writer.write(";\n");
        }
    }

    private long writeRows(BufferedWriter writer, Connection conn, String table,
                           BackupTableSpec.FamilyFilter filter, Long familyId,
                           Set<String> fileRefs) throws SQLException, IOException {
        StringBuilder sql = new StringBuilder("SELECT * FROM `").append(table).append('`');
        if (filter != null) sql.append(" WHERE ").append(filter.where());

        long rows = 0;
        try (PreparedStatement ps = conn.prepareStatement(sql.toString(),
                ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
            // MySQL 流式游标:避免把整表读进内存
            ps.setFetchSize(Integer.MIN_VALUE);
            if (filter != null) {
                for (int i = 1; i <= filter.params(); i++) ps.setLong(i, familyId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int n = meta.getColumnCount();
                StringBuilder cols = new StringBuilder();
                for (int i = 1; i <= n; i++) {
                    if (i > 1) cols.append(',');
                    cols.append('`').append(meta.getColumnLabel(i)).append('`');
                }

                List<String> batch = new ArrayList<>(BATCH_ROWS);
                while (rs.next()) {
                    StringBuilder row = new StringBuilder(128);
                    row.append('(');
                    for (int i = 1; i <= n; i++) {
                        if (i > 1) row.append(',');
                        int type = meta.getColumnType(i);
                        Object value = rs.getObject(i);
                        row.append(BackupSql.literal(value, type));
                        if (value instanceof String s) collectFileRef(s, fileRefs);
                    }
                    row.append(')');
                    batch.add(row.toString());
                    rows++;
                    if (batch.size() >= BATCH_ROWS) {
                        flushBatch(writer, table, cols, batch);
                        batch.clear();
                    }
                }
                if (!batch.isEmpty()) flushBatch(writer, table, cols, batch);
            }
        }
        return rows;
    }

    private void flushBatch(BufferedWriter writer, String table, StringBuilder cols, List<String> batch) throws IOException {
        writer.write("INSERT INTO `" + table + "` (" + cols + ") VALUES\n");
        for (int i = 0; i < batch.size(); i++) {
            writer.write(batch.get(i));
            writer.write(i == batch.size() - 1 ? ";\n" : ",\n");
        }
    }

    private static void collectFileRef(String value, Set<String> out) {
        if (value == null || value.length() < 8) return;
        int idx = value.indexOf("/files/");
        if (idx != 0) return;
        String ref = value;
        int cut = ref.indexOf('?');
        if (cut >= 0) ref = ref.substring(0, cut);
        cut = ref.indexOf('#');
        if (cut >= 0) ref = ref.substring(0, cut);
        if (ref.length() > 7) out.add(ref.substring(7));
    }

    /** 统计实际写入的压缩字节(关闭后 count 才完整,调用方在 try 外读取) */
    static final class CountingOutputStream extends FilterOutputStream {
        private long count;

        CountingOutputStream(OutputStream out) {
            super(out);
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
            count++;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            count += len;
        }
    }
}
