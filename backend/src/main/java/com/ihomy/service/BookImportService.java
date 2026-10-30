package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.CsvReader;
import com.ihomy.common.DictConst;
import com.ihomy.common.ResultCode;
import com.ihomy.entity.BookAccount;
import com.ihomy.entity.BookRecord;
import com.ihomy.mapper.BookAccountMapper;
import com.ihomy.mapper.BookRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 银行流水 CSV 导入:按列映射把文件里的行落成普通记账流水。
 *
 * 解析是流式的(CsvReader 逐条读,不把文件整体读进内存),体积上限 2MB、行数上限 2000;
 * 编码兼容 UTF-8(含 BOM)与 GBK;错误按行汇总返回,不会因为几行脏数据整批 500。
 * 全部合法行在同一个事务里提交;金额列支持货币符号与千分位,类型列可省略(按金额正负判断)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookImportService {

    /** 单次导入最多行数(不含表头) */
    public static final int MAX_ROWS = 2000;
    /** 单次导入最大字节数 */
    public static final long MAX_BYTES = 2L * 1024 * 1024;
    /** 返回给前端的错误明细条数上限(失败总数仍按真实值统计) */
    private static final int MAX_ERRORS = 20;
    /** 编码探测读取的前缀长度 */
    private static final int SNIFF_BYTES = 8192;

    private static final DateTimeFormatter DASH_DATE =
            DateTimeFormatter.ofPattern("uuuu-M-d").withResolverStyle(java.time.format.ResolverStyle.STRICT);
    private static final DateTimeFormatter SLASH_DATE =
            DateTimeFormatter.ofPattern("uuuu/M/d").withResolverStyle(java.time.format.ResolverStyle.STRICT);

    private final BookAccountMapper accountMapper;
    private final BookRecordMapper bookMapper;

    /**
     * 列映射(全部为 0 起的列号;-1 表示不映射)。
     * 日期与金额必填;类型不映射时按金额正负判断;分类/账户/备注都可空。
     */
    public record CsvMapping(int dateCol, int typeCol, int amountCol,
                             int categoryCol, int accountCol, int remarkCol, int headerRows) {
    }

    /** 一条合法行(纯数据,便于单测) */
    record ParsedRow(int line, LocalDate date, String type, BigDecimal amount,
                     String category, String accountName, String remark) {
    }

    /**
     * 导入入口:解析 + 逐行校验 + 批量落库(单事务)。
     *
     * @return { total, success, failed, errors:[{line,message}] }
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importCsv(Long userId, Long familyId, MultipartFile file, CsvMapping mapping) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "请选择要导入的 CSV 文件");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BizException(ResultCode.BAD_REQUEST, "文件超过 2MB，请拆分后再导入");
        }
        validateMapping(mapping);

        Map<String, Long> accountIdByName = accountIds(familyId);
        Charset charset = detectCharset(file);
        List<ParsedRow> valid = new ArrayList<>();
        List<Map<String, Object>> errors = new ArrayList<>();
        int failed = 0;
        int total = 0;

        try (Reader reader = new InputStreamReader(file.getInputStream(), charset);
             CsvReader csv = new CsvReader(reader)) {
            CsvReader.Record record;
            int index = 0;
            boolean first = true;
            while ((record = csv.next()) != null) {
                if (first) {
                    first = false;
                    if (!record.cells().isEmpty()) {
                        record.cells().set(0, stripBom(record.cells().get(0)));
                    }
                }
                if (index++ < mapping.headerRows()) continue; // 跳过表头
                total++;
                if (total > MAX_ROWS) {
                    throw new BizException(ResultCode.BAD_REQUEST, "文件超过 2000 行，请拆分后再导入");
                }
                try {
                    ParsedRow row = parseRow(record, mapping);
                    // 账户按名称匹配家庭账户:匹配不到说明名字写错或账户已删,整行报错而不是悄悄丢掉账户归属
                    if (row.accountName() != null && !accountIdByName.containsKey(row.accountName())) {
                        throw new RowError("账户「" + row.accountName() + "」不存在，请核对账户名称");
                    }
                    valid.add(row);
                } catch (RowError e) {
                    failed++;
                    if (errors.size() < MAX_ERRORS) {
                        Map<String, Object> err = new LinkedHashMap<>();
                        err.put("line", record.startLine());
                        err.put("message", e.getMessage());
                        errors.add(err);
                    }
                }
            }
        } catch (IOException e) {
            log.error("CSV 导入读取失败, familyId={}", familyId, e);
            throw new BizException(ResultCode.BAD_REQUEST, "文件读取失败，请确认是 CSV 文本文件");
        }

        int success = 0;
        for (ParsedRow row : valid) {
            BookRecord r = new BookRecord();
            r.setFamilyId(familyId);
            r.setType(row.type());
            r.setAmount(row.amount());
            r.setCategory(row.category());
            r.setRemark(row.remark());
            r.setRecordDate(row.date());
            r.setCreatedBy(userId);
            r.setAccountId(row.accountName() == null ? null : accountIdByName.get(row.accountName()));
            bookMapper.insert(r);
            success++;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("success", success);
        result.put("failed", failed);
        result.put("errors", errors);
        log.info("CSV 导入完成, familyId={}, total={}, success={}, failed={}", familyId, total, success, failed);
        return result;
    }

    /** 行级校验异常:消息直接给用户看(只说能做什么,不暴露库表/字段) */
    static class RowError extends RuntimeException {
        RowError(String message) {
            super(message);
        }
    }

    /** 一行解析 + 校验(纯函数,不查库):日期/类型/金额/分类/备注的规则都在这里 */
    static ParsedRow parseRow(CsvReader.Record record, CsvMapping mapping) {
        List<String> cells = record.cells();
        String rawDate = cell(cells, mapping.dateCol());
        String rawAmount = cell(cells, mapping.amountCol());
        if (rawDate == null || rawDate.isBlank()) throw new RowError("日期为空");
        LocalDate date = parseDate(rawDate);
        if (date == null) throw new RowError("日期无法识别：" + rawDate.trim());
        if (rawAmount == null || rawAmount.isBlank()) throw new RowError("金额为空");
        BigDecimal signed = parseAmount(rawAmount);
        if (signed == null) throw new RowError("金额无法识别：" + rawAmount.trim());
        if (signed.compareTo(BigDecimal.ZERO) == 0) throw new RowError("金额不能为 0");

        String type;
        String rawType = cell(cells, mapping.typeCol());
        if (rawType != null && !rawType.isBlank()) {
            type = parseType(rawType);
            if (type == null) throw new RowError("类型无法识别：" + rawType.trim());
            if (DictConst.BOOK_TRANSFER.equals(type)) throw new RowError("暂不支持导入转账记录");
        } else {
            type = signed.signum() < 0 ? DictConst.BOOK_EXPENSE : DictConst.BOOK_INCOME;
        }

        String category = cell(cells, mapping.categoryCol());
        category = category == null || category.isBlank() ? "其他" : category.trim();
        if (category.length() > 30) throw new RowError("分类最多 30 字");

        String account = cell(cells, mapping.accountCol());
        account = account == null || account.isBlank() ? null : account.trim();

        String remark = cell(cells, mapping.remarkCol());
        remark = remark == null || remark.isBlank() ? null : remark.trim();
        if (remark != null && remark.length() > 200) throw new RowError("备注最多 200 字");

        return new ParsedRow(record.startLine(), date, type, signed.abs(), category, account, remark);
    }

    /** 日期:支持 2026-01-05 / 2026/1/5 / 2026.1.5 / 2026年1月5日 / 20260105 */
    static LocalDate parseDate(String raw) {
        String s = raw.trim().replace("年", "-").replace("月", "-").replace("日", "")
                .replace('/', '-').replace('.', '-');
        if (s.matches("\\d{8}")) {
            s = s.substring(0, 4) + "-" + s.substring(4, 6) + "-" + s.substring(6, 8);
        }
        try {
            return LocalDate.parse(s, DASH_DATE);
        } catch (DateTimeParseException ignore) {
            // 退而用斜杠格式(已被上面的替换归一,这里只兜底异常格式)
            try {
                return LocalDate.parse(raw.trim(), SLASH_DATE);
            } catch (DateTimeParseException e) {
                return null;
            }
        }
    }

    /** 金额:去掉货币符号/千分位/空格与全角负号;括号负数记为负 */
    static BigDecimal parseAmount(String raw) {
        String s = raw.trim()
                .replace("¥", "").replace("￥", "").replace(",", "").replace(" ", "")
                .replace("−", "-").replace("－", "-");
        boolean negative = false;
        if (s.startsWith("(") && s.endsWith(")")) {
            negative = true;
            s = s.substring(1, s.length() - 1);
        }
        try {
            BigDecimal v = new BigDecimal(s);
            return negative ? v.negate() : v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 类型:中英文都认(支出/收入;debit=支出 credit=收入;转账单独识别后拒绝) */
    static String parseType(String raw) {
        String s = raw.trim().toUpperCase();
        return switch (s) {
            case "支出", "支出(元)", "出账", "付款", "消费", "EXPENSE", "DEBIT" -> DictConst.BOOK_EXPENSE;
            case "收入", "收入(元)", "入账", "收款", "INCOME", "CREDIT" -> DictConst.BOOK_INCOME;
            case "转账", "TRANSFER" -> DictConst.BOOK_TRANSFER;
            default -> null;
        };
    }

    private static String cell(List<String> cells, int col) {
        if (col < 0 || col >= cells.size()) return null;
        return cells.get(col);
    }

    private static String stripBom(String s) {
        return s != null && s.startsWith("\uFEFF") ? s.substring(1) : s;
    }

    private static void validateMapping(CsvMapping mapping) {
        if (mapping.dateCol() < 0 || mapping.amountCol() < 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "请先选择日期列与金额列");
        }
        if (mapping.headerRows() < 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "表头行数不合法");
        }
    }

    private Map<String, Long> accountIds(Long familyId) {
        List<BookAccount> accounts = accountMapper.selectList(new LambdaQueryWrapper<BookAccount>()
                .eq(BookAccount::getFamilyId, familyId));
        Map<String, Long> map = new HashMap<>();
        for (BookAccount a : accounts) {
            if (a.getName() != null) map.putIfAbsent(a.getName().trim(), a.getId());
        }
        return map;
    }

    /** 编码探测:BOM 优先;无 BOM 时按前缀能否用 UTF-8 解码判断,解出乱码则按 GBK */
    static Charset detectCharset(MultipartFile file) {
        byte[] buf = new byte[SNIFF_BYTES];
        int n = 0;
        try (var in = file.getInputStream()) {
            n = in.read(buf, 0, buf.length);
        } catch (IOException e) {
            throw new BizException(ResultCode.BAD_REQUEST, "文件读取失败，请确认是 CSV 文本文件");
        }
        if (n <= 0) return StandardCharsets.UTF_8;
        if (n >= 3 && (buf[0] & 0xFF) == 0xEF && (buf[1] & 0xFF) == 0xBB && (buf[2] & 0xFF) == 0xBF) {
            return StandardCharsets.UTF_8;
        }
        int len = trimIncompleteUtf8(buf, n);
        String probe = new String(buf, 0, len, StandardCharsets.UTF_8);
        return probe.indexOf('\uFFFD') >= 0 ? Charset.forName("GBK") : StandardCharsets.UTF_8;
    }

    /** 前缀可能截断在某个多字节字符中间:去掉末尾不完整序列,避免误判成 GBK */
    private static int trimIncompleteUtf8(byte[] buf, int n) {
        // 从末尾往前最多找 4 字节,定位最后一个起始字节
        for (int back = 0; back < 4 && back < n; back++) {
            int idx = n - 1 - back;
            int b = buf[idx] & 0xFF;
            if ((b & 0xC0) == 0x80) continue; // 续字节,继续往前找
            int need = b < 0x80 ? 1
                    : (b & 0xE0) == 0xC0 ? 2
                    : (b & 0xF0) == 0xE0 ? 3
                    : (b & 0xF8) == 0xF0 ? 4 : 0;
            if (need == 0) return n; // 非法起始字节,让 UTF-8 解码出替换符 → 判成 GBK
            return (n - idx) < need ? idx : n; // 不完整则截到该字符前
        }
        return n;
    }
}
