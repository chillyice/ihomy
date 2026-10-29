package com.ihomy.common;

import com.ihomy.entity.BookRecord;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 家庭总账聚合(纯函数,不查库):把一个月/全时段的记账明细算成「累计 + 期初结余 → 逐期滚动 → 期末结余」。
 *
 * 口径(前后端同一套,前端 utils/bookLedger.js 只做展示层补零与同比):
 * - INCOME / EXPENSE 计入收支与结余,TRANSFER 永不计入结余,只单独统计笔数与金额;
 * - count 为收支笔数,不含转账;
 * - opening = 该范围开始之前的累计结余,closing = opening + income − expense,逐期严格闭合;
 * - 比例一律 HALF_UP 保留 4 位,合计为 0 时给 0(不除零);
 * - totals 恒为全时段,不随 scope 变;分类/成员/未归属跟随 scope。
 *
 * ponytail: 家庭账本量级(≤ 万行)一次取回内存汇总最简单;单家庭超过 5 万行时改 SQL GROUP BY 分年分月聚合。
 */
public final class BookLedgerUtil {

    private static final int MONEY_SCALE = 2;
    private static final int RATIO_SCALE = 4;

    private BookLedgerUtil() {
    }

    /**
     * @param records 该家庭全部记账明细,调用方须按 recordDate,id 升序查好
     * @param year    统计范围:null/0 表示全部;否则只统计该年
     */
    public static Map<String, Object> aggregate(List<BookRecord> records, Integer year) {
        Integer scopeYear = (year == null || year <= 0) ? null : year;

        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;
        long totalCount = 0;
        long transferCount = 0;
        BigDecimal transferAmount = BigDecimal.ZERO;
        LocalDate firstDate = null;
        LocalDate lastDate = null;

        Map<Integer, Sum> byYear = new TreeMap<>();
        Map<YearMonth, Sum> byMonth = new TreeMap<>();
        TreeSet<Integer> touchedYears = new TreeSet<>();
        Map<String, CatSum> expenseCats = new LinkedHashMap<>();
        Map<String, CatSum> incomeCats = new LinkedHashMap<>();
        Map<Long, MemberSum> members = new LinkedHashMap<>();
        long unassignedCount = 0;
        BigDecimal unassignedIncome = BigDecimal.ZERO;
        BigDecimal unassignedExpense = BigDecimal.ZERO;

        for (BookRecord r : records) {
            LocalDate date = r.getRecordDate();
            if (date == null) continue;
            BigDecimal amount = r.getAmount() == null ? BigDecimal.ZERO : r.getAmount();
            String type = DictConst.bookType(r.getType());
            boolean inScope = scopeYear == null || date.getYear() == scopeYear;

            if (firstDate == null || date.isBefore(firstDate)) firstDate = date;
            if (lastDate == null || date.isAfter(lastDate)) lastDate = date;
            touchedYears.add(date.getYear());

            if (DictConst.BOOK_TRANSFER.equals(type)) {
                transferCount++;
                transferAmount = transferAmount.add(amount);
            } else {
                byYear.computeIfAbsent(date.getYear(), y -> new Sum()).add(type, amount);
                byMonth.computeIfAbsent(YearMonth.from(date), m -> new Sum()).add(type, amount);
                if (DictConst.BOOK_INCOME.equals(type)) totalIncome = totalIncome.add(amount);
                else totalExpense = totalExpense.add(amount);
                totalCount++;
            }

            if (!inScope) continue;

            if (DictConst.BOOK_INCOME.equals(type)) {
                incomeCats.computeIfAbsent(categoryOf(r), c -> new CatSum()).add(amount);
            } else if (DictConst.BOOK_EXPENSE.equals(type)) {
                expenseCats.computeIfAbsent(categoryOf(r), c -> new CatSum()).add(amount);
            }
            if (r.getCreatedBy() != null && !DictConst.BOOK_TRANSFER.equals(type)) {
                members.computeIfAbsent(r.getCreatedBy(), u -> new MemberSum()).add(type, amount);
            }
            if (r.getAccountId() == null && r.getFromAccountId() == null && r.getToAccountId() == null) {
                unassignedCount++;
                if (DictConst.BOOK_INCOME.equals(type)) unassignedIncome = unassignedIncome.add(amount);
                else if (DictConst.BOOK_EXPENSE.equals(type)) unassignedExpense = unassignedExpense.add(amount);
            }
        }

        // 年度逐期滚动:opening 从 0 起,closing 传下一年
        List<Map<String, Object>> yearRows = new ArrayList<>();
        BigDecimal rolling = BigDecimal.ZERO;
        for (Map.Entry<Integer, Sum> e : byYear.entrySet()) {
            Sum s = e.getValue();
            BigDecimal opening = rolling;
            BigDecimal closing = opening.add(s.income).subtract(s.expense);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("year", e.getKey());
            row.put("income", money(s.income));
            row.put("expense", money(s.expense));
            row.put("balance", money(s.income.subtract(s.expense)));
            row.put("count", s.count);
            row.put("opening", money(opening));
            row.put("closing", money(closing));
            yearRows.add(row);
            rolling = closing;
        }

        // 月度逐期滚动:全时段先算一遍,再按 scope 过滤,保证某年的期初等于此前累计
        Map<YearMonth, Map<String, Object>> monthRows = new LinkedHashMap<>();
        BigDecimal monthRolling = BigDecimal.ZERO;
        for (Map.Entry<YearMonth, Sum> e : byMonth.entrySet()) {
            Sum s = e.getValue();
            BigDecimal opening = monthRolling;
            BigDecimal closing = opening.add(s.income).subtract(s.expense);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("month", e.getKey().toString());
            row.put("income", money(s.income));
            row.put("expense", money(s.expense));
            row.put("balance", money(s.income.subtract(s.expense)));
            row.put("count", s.count);
            row.put("opening", money(opening));
            row.put("closing", money(closing));
            monthRows.put(e.getKey(), row);
            monthRolling = closing;
        }

        String scope = scopeYear == null ? "ALL" : String.valueOf(scopeYear);
        BigDecimal opening = scopeYear == null ? BigDecimal.ZERO : openingOf(yearRows, scopeYear);

        List<Map<String, Object>> byMonthRows = new ArrayList<>();
        for (Map.Entry<YearMonth, Map<String, Object>> e : monthRows.entrySet()) {
            if (scopeYear == null || e.getKey().getYear() == scopeYear) {
                byMonthRows.add(e.getValue());
            }
        }

        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("income", money(totalIncome));
        totals.put("expense", money(totalExpense));
        totals.put("balance", money(totalIncome.subtract(totalExpense)));
        totals.put("count", totalCount);
        totals.put("transferCount", transferCount);
        totals.put("transferAmount", money(transferAmount));
        totals.put("firstDate", firstDate);
        totals.put("lastDate", lastDate);

        Map<String, Object> unassigned = new LinkedHashMap<>();
        unassigned.put("count", unassignedCount);
        unassigned.put("income", money(unassignedIncome));
        unassigned.put("expense", money(unassignedExpense));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scope", scope);
        result.put("years", new ArrayList<>(touchedYears.descendingSet()));
        result.put("totals", totals);
        result.put("opening", money(opening));
        result.put("byYear", yearRows);
        result.put("byMonth", byMonthRows);
        result.put("expenseByCategory", categoryRows(expenseCats));
        result.put("incomeByCategory", categoryRows(incomeCats));
        result.put("byMember", memberRows(members));
        result.put("unassigned", unassigned);
        return result;
    }

    /** 该年的期初结余:所有更早年份的 closing 累加;早于最早记录 → 0 */
    private static BigDecimal openingOf(List<Map<String, Object>> yearRows, int year) {
        BigDecimal opening = BigDecimal.ZERO;
        for (Map<String, Object> row : yearRows) {
            if ((Integer) row.get("year") >= year) break;
            opening = (BigDecimal) row.get("closing");
        }
        return opening;
    }

    private static String categoryOf(BookRecord r) {
        String category = r.getCategory();
        return category == null || category.isBlank() ? "其他" : category;
    }

    private static BigDecimal money(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal ratio(BigDecimal total, BigDecimal sum) {
        if (sum == null || sum.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO.setScale(RATIO_SCALE);
        return total.divide(sum, RATIO_SCALE, RoundingMode.HALF_UP);
    }

    /** 分类排行:金额从大到小,同额按分类名 */
    private static List<Map<String, Object>> categoryRows(Map<String, CatSum> cats) {
        BigDecimal sum = cats.values().stream().map(c -> c.total).reduce(BigDecimal.ZERO, BigDecimal::add);
        return cats.entrySet().stream()
                .sorted((a, b) -> {
                    int byTotal = b.getValue().total.compareTo(a.getValue().total);
                    return byTotal != 0 ? byTotal : a.getKey().compareTo(b.getKey());
                })
                .map(e -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("category", e.getKey());
                    row.put("total", money(e.getValue().total));
                    row.put("count", e.getValue().count);
                    row.put("ratio", ratio(e.getValue().total, sum));
                    return row;
                })
                .toList();
    }

    /** 成员收支:收支合计从大到小,同额按用户 id */
    private static List<Map<String, Object>> memberRows(Map<Long, MemberSum> members) {
        return members.entrySet().stream()
                .sorted((a, b) -> {
                    BigDecimal sa = a.getValue().income.add(a.getValue().expense);
                    BigDecimal sb = b.getValue().income.add(b.getValue().expense);
                    int bySum = sb.compareTo(sa);
                    return bySum != 0 ? bySum : a.getKey().compareTo(b.getKey());
                })
                .map(e -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("userId", e.getKey());
                    row.put("income", money(e.getValue().income));
                    row.put("expense", money(e.getValue().expense));
                    row.put("count", e.getValue().count);
                    return row;
                })
                .toList();
    }

    /** 年度/月份累计桶(只放收支,转账不进来) */
    private static final class Sum {
        private BigDecimal income = BigDecimal.ZERO;
        private BigDecimal expense = BigDecimal.ZERO;
        private long count = 0;

        private void add(String type, BigDecimal amount) {
            if (DictConst.BOOK_INCOME.equals(type)) income = income.add(amount);
            else expense = expense.add(amount);
            count++;
        }
    }

    /** 分类累计 */
    private static final class CatSum {
        private BigDecimal total = BigDecimal.ZERO;
        private long count = 0;

        private void add(BigDecimal amount) {
            total = total.add(amount);
            count++;
        }
    }

    /** 成员累计 */
    private static final class MemberSum {
        private BigDecimal income = BigDecimal.ZERO;
        private BigDecimal expense = BigDecimal.ZERO;
        private long count = 0;

        private void add(String type, BigDecimal amount) {
            if (DictConst.BOOK_INCOME.equals(type)) income = income.add(amount);
            else expense = expense.add(amount);
            count++;
        }
    }
}
