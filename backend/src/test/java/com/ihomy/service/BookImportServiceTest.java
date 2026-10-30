package com.ihomy.service;

import com.ihomy.common.CsvReader;
import com.ihomy.common.DictConst;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 银行流水导入的行级规则(纯逻辑,不起 Spring/DB):
 * 日期多格式、金额去符号与千分位、括号负数、类型可省略按正负判断、
 * 坏日期/坏金额/转账/超长分类都按行报错,不影响其余行。
 */
class BookImportServiceTest {

    private static final BookImportService.CsvMapping MAPPING =
            new BookImportService.CsvMapping(0, 1, 2, 3, -1, 4, 1);

    private static CsvReader.Record record(String... cells) {
        return new CsvReader.Record(2, List.of(cells));
    }

    @Test
    void parsesCommonDateFormats() {
        assertThat(BookImportService.parseDate("2026-01-05")).isEqualTo(LocalDate.of(2026, 1, 5));
        assertThat(BookImportService.parseDate("2026/1/5")).isEqualTo(LocalDate.of(2026, 1, 5));
        assertThat(BookImportService.parseDate("2026.1.5")).isEqualTo(LocalDate.of(2026, 1, 5));
        assertThat(BookImportService.parseDate("2026年1月5日")).isEqualTo(LocalDate.of(2026, 1, 5));
        assertThat(BookImportService.parseDate("20260105")).isEqualTo(LocalDate.of(2026, 1, 5));
    }

    @Test
    void rejectsImpossibleDates() {
        assertThat(BookImportService.parseDate("2026-02-30")).isNull();
        assertThat(BookImportService.parseDate("2026-13-01")).isNull();
        assertThat(BookImportService.parseDate("昨天")).isNull();
    }

    @Test
    void parsesAmountsWithCurrencySymbolsAndThousandSeparators() {
        assertThat(BookImportService.parseAmount("¥1,234.50")).isEqualByComparingTo("1234.50");
        assertThat(BookImportService.parseAmount(" 300 ")).isEqualByComparingTo("300");
        assertThat(BookImportService.parseAmount("(300)")).isEqualByComparingTo("-300");
        assertThat(BookImportService.parseAmount("abc")).isNull();
    }

    @Test
    void parsesTypeInChineseAndEnglish() {
        assertThat(BookImportService.parseType("支出")).isEqualTo(DictConst.BOOK_EXPENSE);
        assertThat(BookImportService.parseType("expense")).isEqualTo(DictConst.BOOK_EXPENSE);
        assertThat(BookImportService.parseType("收入")).isEqualTo(DictConst.BOOK_INCOME);
        assertThat(BookImportService.parseType("debit")).isEqualTo(DictConst.BOOK_EXPENSE);
        assertThat(BookImportService.parseType("转账")).isEqualTo(DictConst.BOOK_TRANSFER);
        assertThat(BookImportService.parseType("未知")).isNull();
    }

    @Test
    void validRowKeepsTypeAndAbsoluteAmount() {
        BookImportService.ParsedRow row = BookImportService.parseRow(
                record("2026-01-05", "支出", "-300.00", "餐饮", "超市"), MAPPING);

        assertThat(row.date()).isEqualTo(LocalDate.of(2026, 1, 5));
        assertThat(row.type()).isEqualTo(DictConst.BOOK_EXPENSE);
        assertThat(row.amount()).isEqualByComparingTo("300.00");
        assertThat(row.category()).isEqualTo("餐饮");
        assertThat(row.remark()).isEqualTo("超市");
    }

    @Test
    void blankTypeInfersFromAmountSign() {
        BookImportService.ParsedRow expense = BookImportService.parseRow(
                record("2026-01-05", "", "-12.5", "", ""), MAPPING);
        assertThat(expense.type()).isEqualTo(DictConst.BOOK_EXPENSE);
        assertThat(expense.amount()).isEqualByComparingTo("12.5");
        assertThat(expense.category()).isEqualTo("其他");

        BookImportService.ParsedRow income = BookImportService.parseRow(
                record("2026-01-05", "", "8000", "", "工资"), MAPPING);
        assertThat(income.type()).isEqualTo(DictConst.BOOK_INCOME);
    }

    @Test
    void badDateRowIsRejectedWithReadableMessage() {
        assertThatThrownBy(() -> BookImportService.parseRow(
                record("2026-02-30", "支出", "10", "", ""), MAPPING))
                .isInstanceOf(BookImportService.RowError.class)
                .hasMessageContaining("日期无法识别");
    }

    @Test
    void zeroAmountAndTransferAreRejected() {
        assertThatThrownBy(() -> BookImportService.parseRow(
                record("2026-01-05", "支出", "0", "", ""), MAPPING))
                .hasMessageContaining("金额不能为 0");
        assertThatThrownBy(() -> BookImportService.parseRow(
                record("2026-01-05", "转账", "10", "", ""), MAPPING))
                .hasMessageContaining("暂不支持导入转账");
    }

    @Test
    void tooLongCategoryIsRejected() {
        String longCategory = "分类".repeat(16); // 32 字 > 30
        assertThatThrownBy(() -> BookImportService.parseRow(
                record("2026-01-05", "支出", "10", longCategory, ""), MAPPING))
                .hasMessageContaining("分类最多 30 字");
    }

    @Test
    void detectsUtf8WithBomAndGbkWithoutBom() {
        byte[] utf8 = "日期,金额\n2026-01-05,10\n".getBytes(StandardCharsets.UTF_8);
        assertThat(BookImportService.detectCharset(file(utf8))).isEqualTo(StandardCharsets.UTF_8);

        byte[] withBom = new byte[utf8.length + 3];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(utf8, 0, withBom, 3, utf8.length);
        assertThat(BookImportService.detectCharset(file(withBom))).isEqualTo(StandardCharsets.UTF_8);

        byte[] gbk = "日期,金额\n2026-01-05,10\n".getBytes(Charset.forName("GBK"));
        assertThat(BookImportService.detectCharset(file(gbk))).isEqualTo(Charset.forName("GBK"));
    }

    @Test
    void amountIsComparedByValueNotScale() {
        assertThat(BookImportService.parseAmount("100")).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    private static MockMultipartFile file(byte[] bytes) {
        return new MockMultipartFile("file", "book.csv", "text/csv", bytes);
    }
}
