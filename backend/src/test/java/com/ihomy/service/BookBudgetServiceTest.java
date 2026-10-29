package com.ihomy.service;

import com.ihomy.entity.BookBudget;
import com.ihomy.entity.BookRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 预算与当月支出对比的口径(纯函数,不碰 Spring/DB):
 * 只算支出(收入/转账不参与);进度比按「已花 ÷ 预算」、超支即 >1;未设预算的支出单列;合计不被稀释。
 */
class BookBudgetServiceTest {

    @Test
    void emptyBudgetAndNoRecordsGivesZeroTotals() {
        Map<String, Object> data = BookBudgetService.compare(List.of(), List.of());

        assertThat((List<?>) data.get("items")).isEmpty();
        assertThat(data.get("overCount")).isEqualTo(0);
        assertThat(data.get("unbudgeted")).isEqualTo(money("0.00"));
        Map<String, Object> total = total(data);
        assertThat(total.get("budget")).isEqualTo(money("0.00"));
        assertThat(total.get("spent")).isEqualTo(money("0.00"));
        assertThat(total.get("ratio")).isEqualTo(ratio("0.0000"));
        assertThat(total.get("over")).isEqualTo(false);
    }

    @Test
    void spentIsComparedPerCategoryAndOverBudgetIsFlagged() {
        Map<String, Object> data = BookBudgetService.compare(
                List.of(budget(1L, "餐饮", "2000.00"), budget(2L, "交通", "500.00")),
                List.of(expense("餐饮", "2300.00"), expense("交通", "120.50")));

        List<Map<String, Object>> items = items(data);
        // 超支的排在最前(进度比倒序)
        assertThat(items.get(0).get("category")).isEqualTo("餐饮");
        assertThat(items.get(0).get("spent")).isEqualTo(money("2300.00"));
        assertThat(items.get(0).get("remaining")).isEqualTo(money("-300.00"));
        assertThat(items.get(0).get("excess")).isEqualTo(money("300.00"));
        assertThat(items.get(0).get("over")).isEqualTo(true);
        assertThat((BigDecimal) items.get(0).get("ratio")).isEqualByComparingTo("1.1500");

        assertThat(items.get(1).get("category")).isEqualTo("交通");
        assertThat(items.get(1).get("remaining")).isEqualTo(money("379.50"));
        assertThat(items.get(1).get("excess")).isEqualTo(money("0.00"));
        assertThat(items.get(1).get("over")).isEqualTo(false);

        assertThat(data.get("overCount")).isEqualTo(1);
        Map<String, Object> total = total(data);
        assertThat(total.get("budget")).isEqualTo(money("2500.00"));
        assertThat(total.get("spent")).isEqualTo(money("2420.50"));
        assertThat(total.get("over")).isEqualTo(false);
    }

    @Test
    void incomeAndTransferNeverCount() {
        BookRecord income = record("INCOME", "餐饮", "999.00", "2026-10-03");
        BookRecord transfer = record("TRANSFER", "餐饮", "888.00", "2026-10-04");
        Map<String, Object> data = BookBudgetService.compare(
                List.of(budget(1L, "餐饮", "100.00")),
                List.of(income, transfer, expense("餐饮", "60.00")));

        Map<String, Object> item = items(data).get(0);
        assertThat(item.get("spent")).isEqualTo(money("60.00"));
        assertThat(item.get("over")).isEqualTo(false);
        assertThat(data.get("unbudgeted")).isEqualTo(money("0.00"));
    }

    @Test
    void unbudgetedSpendingIsReportedSeparately() {
        Map<String, Object> data = BookBudgetService.compare(
                List.of(budget(1L, "餐饮", "1000.00")),
                List.of(expense("餐饮", "200.00"), expense("购物", "350.00"), expense("医疗", "50.00")));

        assertThat(items(data).get(0).get("spent")).isEqualTo(money("200.00"));
        assertThat(data.get("unbudgeted")).isEqualTo(money("400.00"));
        Map<String, Object> total = total(data);
        // 合计只算有预算的分类,进度比不被未设预算的支出稀释
        assertThat(total.get("spent")).isEqualTo(money("200.00"));
        assertThat((BigDecimal) total.get("ratio")).isEqualByComparingTo("0.2000");
    }

    @Test
    void blankCategoryFallsBackToOther() {
        BookRecord noCategory = record("EXPENSE", " ", "80.00", "2026-10-05");
        noCategory.setCategory(null);
        Map<String, Object> data = BookBudgetService.compare(
                List.of(budget(1L, "其他", "100.00")),
                List.of(noCategory));

        assertThat(items(data).get(0).get("spent")).isEqualTo(money("80.00"));
    }

    private static BookBudget budget(Long id, String category, String amount) {
        BookBudget b = new BookBudget();
        b.setId(id);
        b.setCategory(category);
        b.setAmount(new BigDecimal(amount));
        return b;
    }

    private static BookRecord expense(String category, String amount) {
        return record("EXPENSE", category, amount, "2026-10-01");
    }

    private static BookRecord record(String type, String category, String amount, String date) {
        BookRecord r = new BookRecord();
        r.setType(type);
        r.setCategory(category);
        r.setAmount(new BigDecimal(amount));
        r.setRecordDate(LocalDate.parse(date));
        return r;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(Map<String, Object> data) {
        return (List<Map<String, Object>>) data.get("items");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> total(Map<String, Object> data) {
        return (Map<String, Object>) data.get("total");
    }

    private static BigDecimal money(String v) {
        return new BigDecimal(v).setScale(2);
    }

    private static BigDecimal ratio(String v) {
        return new BigDecimal(v).setScale(4);
    }
}
