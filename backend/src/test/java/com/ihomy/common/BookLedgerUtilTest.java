package com.ihomy.common;

import com.ihomy.entity.BookRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 总账聚合口径(纯函数,不碰 Spring/DB):
 * 收支进结余、转账只单独计数;期初→逐期期末严格闭合;分类/成员/未归属跟随范围;比例 4 位小数不除零。
 */
class BookLedgerUtilTest {

    @Test
    void emptyLedgerGivesZerosAndNoYears() {
        Map<String, Object> ledger = BookLedgerUtil.aggregate(List.of(), null);

        assertThat(ledger.get("scope")).isEqualTo("ALL");
        assertThat((List<?>) ledger.get("years")).isEmpty();
        Map<String, Object> totals = totals(ledger);
        assertThat(totals.get("income")).isEqualTo(money("0.00"));
        assertThat(totals.get("expense")).isEqualTo(money("0.00"));
        assertThat(totals.get("balance")).isEqualTo(money("0.00"));
        assertThat(totals.get("count")).isEqualTo(0L);
        assertThat(totals.get("transferCount")).isEqualTo(0L);
        assertThat(totals.get("firstDate")).isNull();
        assertThat((List<?>) ledger.get("byYear")).isEmpty();
        assertThat((List<?>) ledger.get("byMonth")).isEmpty();
        assertThat(ledger.get("opening")).isEqualTo(money("0.00"));
        assertThat(unassigned(ledger).get("count")).isEqualTo(0L);
    }

    @Test
    void transfersCountSeparatelyAndNeverTouchBalance() {
        Map<String, Object> ledger = BookLedgerUtil.aggregate(sorted(List.of(
                record("TRANSFER", "500.00", "2025-03-02", 1L),
                record("TRANSFER", "250.00", "2025-04-02", 1L))), null);

        Map<String, Object> totals = totals(ledger);
        assertThat(totals.get("balance")).isEqualTo(money("0.00"));
        assertThat(totals.get("income")).isEqualTo(money("0.00"));
        assertThat(totals.get("expense")).isEqualTo(money("0.00"));
        assertThat(totals.get("count")).isEqualTo(0L);
        assertThat(totals.get("transferCount")).isEqualTo(2L);
        assertThat(totals.get("transferAmount")).isEqualTo(money("750.00"));
        // 只有转账的年份也要出现在年份筛选里,但不产生年度行(无收支)
        assertThat(years(ledger)).containsExactly(2025);
        assertThat((List<?>) ledger.get("byYear")).isEmpty();
        assertThat((List<?>) ledger.get("byMonth")).isEmpty();
    }

    @Test
    void monthlyOpeningAndClosingRollOver() {
        Map<String, Object> ledger = BookLedgerUtil.aggregate(sorted(List.of(
                record("INCOME", "1000.00", "2026-01-05", 1L),
                record("EXPENSE", "300.00", "2026-01-20", 1L),
                record("EXPENSE", "200.00", "2026-03-10", 1L))), 2026);

        List<Map<String, Object>> months = rows(ledger, "byMonth");
        // 二月没记录就不出行,前端补 12 槽
        assertThat(months).hasSize(2);

        assertThat(months.get(0).get("month")).isEqualTo("2026-01");
        assertThat(months.get(0).get("opening")).isEqualTo(money("0.00"));
        assertThat(months.get(0).get("income")).isEqualTo(money("1000.00"));
        assertThat(months.get(0).get("expense")).isEqualTo(money("300.00"));
        assertThat(months.get(0).get("closing")).isEqualTo(money("700.00"));

        assertThat(months.get(1).get("month")).isEqualTo("2026-03");
        assertThat(months.get(1).get("opening")).isEqualTo(money("700.00"));
        assertThat(months.get(1).get("expense")).isEqualTo(money("200.00"));
        assertThat(months.get(1).get("closing")).isEqualTo(money("500.00"));

        // 期末与累计闭合
        assertThat(totals(ledger).get("balance")).isEqualTo(money("500.00"));
        assertThat(months.get(1).get("closing")).isEqualTo(totals(ledger).get("balance"));
    }

    @Test
    void scopedYearOpeningCarriesEarlierHistory() {
        List<BookRecord> records = sorted(List.of(
                record("INCOME", "5000.00", "2024-06-01", 1L),
                record("EXPENSE", "1000.00", "2024-12-31", 1L),
                record("INCOME", "3000.00", "2025-02-01", 1L),
                record("EXPENSE", "800.00", "2026-01-15", 1L)));

        Map<String, Object> all = BookLedgerUtil.aggregate(records, null);
        assertThat(all.get("scope")).isEqualTo("ALL");
        assertThat(all.get("opening")).isEqualTo(money("0.00"));
        assertThat(totals(all).get("income")).isEqualTo(money("8000.00"));
        assertThat(totals(all).get("expense")).isEqualTo(money("1800.00"));
        assertThat(years(all)).containsExactly(2026, 2025, 2024);

        List<Map<String, Object>> years = rows(all, "byYear");
        assertThat(years).hasSize(3);
        assertThat(years.get(0).get("year")).isEqualTo(2024);
        assertThat(years.get(0).get("opening")).isEqualTo(money("0.00"));
        assertThat(years.get(0).get("closing")).isEqualTo(money("4000.00"));
        assertThat(years.get(1).get("year")).isEqualTo(2025);
        assertThat(years.get(1).get("opening")).isEqualTo(money("4000.00"));
        assertThat(years.get(1).get("closing")).isEqualTo(money("7000.00"));
        assertThat(years.get(2).get("year")).isEqualTo(2026);
        assertThat(years.get(2).get("opening")).isEqualTo(money("7000.00"));
        assertThat(years.get(2).get("closing")).isEqualTo(money("6200.00"));

        Map<String, Object> y2026 = BookLedgerUtil.aggregate(records, 2026);
        assertThat(y2026.get("scope")).isEqualTo("2026");
        // totals 恒为全时段,不随范围变
        assertThat(totals(y2026)).isEqualTo(totals(all));
        // 期初 = 该年之前累计结余
        assertThat(y2026.get("opening")).isEqualTo(money("7000.00"));
        List<Map<String, Object>> months = rows(y2026, "byMonth");
        assertThat(months).hasSize(1);
        assertThat(months.get(0).get("month")).isEqualTo("2026-01");
        assertThat(months.get(0).get("opening")).isEqualTo(money("7000.00"));
        assertThat(months.get(0).get("closing")).isEqualTo(money("6200.00"));

        // 范围外的年份不出现月度行
        Map<String, Object> y2024 = BookLedgerUtil.aggregate(records, 2024);
        assertThat(y2024.get("opening")).isEqualTo(money("0.00"));
        assertThat(rows(y2024, "byMonth")).extracting(m -> m.get("month"))
                .containsExactly("2024-06", "2024-12");
        // byYear 始终是全时段(年度同比要用)
        assertThat(rows(y2024, "byYear")).hasSize(3);
    }

    @Test
    void categoriesFollowScopeAndRatiosSumToOne() {
        List<BookRecord> records = sorted(List.of(
                record("EXPENSE", "300.00", "2025-05-01", 1L, "餐饮"),
                record("EXPENSE", "100.00", "2025-05-02", 1L, "交通"),
                record("EXPENSE", "600.00", "2026-05-03", 1L, "餐饮"),
                record("INCOME", "1000.00", "2026-05-04", 1L, "工资"),
                record("INCOME", "0.00", "2026-05-05", 1L, "红包")));

        Map<String, Object> all = BookLedgerUtil.aggregate(records, null);
        List<Map<String, Object>> expenseAll = rows(all, "expenseByCategory");
        assertThat(expenseAll).extracting(m -> m.get("category")).containsExactly("餐饮", "交通");
        assertThat(expenseAll.get(0).get("total")).isEqualTo(money("900.00"));
        assertThat(expenseAll.get(0).get("count")).isEqualTo(2L);
        assertThat(((BigDecimal) expenseAll.get(0).get("ratio")).doubleValue()).isCloseTo(0.9, within(0.0001));
        assertRatioSumsToOne(expenseAll);
        assertRatioSumsToOne(rows(all, "incomeByCategory"));

        // 只看 2025:餐饮 300、交通 100
        Map<String, Object> y2025 = BookLedgerUtil.aggregate(records, 2025);
        List<Map<String, Object>> expense2025 = rows(y2025, "expenseByCategory");
        assertThat(expense2025).hasSize(2);
        assertThat(expense2025.get(0).get("total")).isEqualTo(money("300.00"));
        assertRatioSumsToOne(expense2025);
        // 2026 的工资不会出现在 2025 范围的收入构成里
        Map<String, Object> y2024 = BookLedgerUtil.aggregate(records, 2024);
        assertThat((List<?>) y2024.get("incomeByCategory")).isEmpty();
        assertThat((List<?>) y2024.get("expenseByCategory")).isEmpty();
    }

    @Test
    void ratioIsZeroWhenNothingToSplit() {
        Map<String, Object> ledger = BookLedgerUtil.aggregate(
                sorted(List.of(record("INCOME", "0.00", "2026-02-01", 1L, "红包"))), null);

        List<Map<String, Object>> incomes = rows(ledger, "incomeByCategory");
        assertThat(incomes).hasSize(1);
        // 合计 0:比例给 0,不出现 NaN/Infinity
        assertThat(incomes.get(0).get("ratio")).isEqualTo(new BigDecimal("0.0000"));
    }

    @Test
    void membersCountIncomeAndExpenseButNotTransfers() {
        Map<String, Object> ledger = BookLedgerUtil.aggregate(sorted(List.of(
                record("INCOME", "2000.00", "2026-01-01", 11L),
                record("EXPENSE", "500.00", "2026-01-02", 11L),
                record("EXPENSE", "700.00", "2026-01-03", 22L),
                record("TRANSFER", "400.00", "2026-01-04", 11L))), 2026);

        List<Map<String, Object>> members = rows(ledger, "byMember");
        assertThat(members).hasSize(2);
        // 按收支合计从大到小:11 号 2500,22 号 700
        assertThat(members.get(0).get("userId")).isEqualTo(11L);
        assertThat(members.get(0).get("income")).isEqualTo(money("2000.00"));
        assertThat(members.get(0).get("expense")).isEqualTo(money("500.00"));
        assertThat(members.get(0).get("count")).isEqualTo(2L);
        assertThat(members.get(1).get("userId")).isEqualTo(22L);
        assertThat(members.get(1).get("income")).isEqualTo(money("0.00"));
        assertThat(members.get(1).get("expense")).isEqualTo(money("700.00"));
        // 转账不计入任何成员的笔数
        assertThat(members).extracting(m -> m.get("count")).containsExactly(2L, 1L);
    }

    @Test
    void unassignedRecordsCountedInsideScope() {
        BookRecord noAccount = record("EXPENSE", "66.00", "2025-04-01", 1L);
        BookRecord withAccount = record("EXPENSE", "34.00", "2025-04-02", 1L);
        withAccount.setAccountId(9L);
        BookRecord income = record("INCOME", "20.00", "2026-04-03", 1L);

        Map<String, Object> y2025 = BookLedgerUtil.aggregate(sorted(List.of(noAccount, withAccount, income)), 2025);
        Map<String, Object> unassigned = unassigned(y2025);
        assertThat(unassigned.get("count")).isEqualTo(1L);
        assertThat(unassigned.get("expense")).isEqualTo(money("66.00"));
        assertThat(unassigned.get("income")).isEqualTo(money("0.00"));

        Map<String, Object> all = BookLedgerUtil.aggregate(sorted(List.of(noAccount, withAccount, income)), null);
        assertThat(unassigned(all).get("count")).isEqualTo(2L);
        assertThat(unassigned(all).get("income")).isEqualTo(money("20.00"));
        assertThat(unassigned(all).get("expense")).isEqualTo(money("66.00"));
    }

    @Test
    void yearZeroMeansAllScope() {
        Map<String, Object> ledger = BookLedgerUtil.aggregate(
                sorted(List.of(record("INCOME", "10.00", "2025-01-01", 1L))), 0);
        assertThat(ledger.get("scope")).isEqualTo("ALL");
        assertThat(ledger.get("opening")).isEqualTo(money("0.00"));
    }

    private static void assertRatioSumsToOne(List<Map<String, Object>> rows) {
        double sum = rows.stream().mapToDouble(r -> ((BigDecimal) r.get("ratio")).doubleValue()).sum();
        assertThat(sum).isCloseTo(1.0, within(0.0001));
    }

    @SuppressWarnings("unchecked")
    private static List<Integer> years(Map<String, Object> ledger) {
        return (List<Integer>) ledger.get("years");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> totals(Map<String, Object> ledger) {
        return (Map<String, Object>) ledger.get("totals");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> unassigned(Map<String, Object> ledger) {
        return (Map<String, Object>) ledger.get("unassigned");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Map<String, Object> ledger, String key) {
        return (List<Map<String, Object>>) ledger.get(key);
    }

    private static BigDecimal money(String v) {
        return new BigDecimal(v).setScale(2);
    }

    private static List<BookRecord> sorted(List<BookRecord> records) {
        List<BookRecord> copy = new ArrayList<>(records);
        copy.sort(Comparator.comparing(BookRecord::getRecordDate));
        return copy;
    }

    private static BookRecord record(String type, String amount, String date, Long createdBy) {
        return record(type, amount, date, createdBy, "其他");
    }

    private static BookRecord record(String type, String amount, String date, Long createdBy, String category) {
        BookRecord r = new BookRecord();
        r.setType(type);
        r.setAmount(new BigDecimal(amount));
        r.setRecordDate(LocalDate.parse(date));
        r.setCreatedBy(createdBy);
        r.setCategory(category);
        return r;
    }
}
