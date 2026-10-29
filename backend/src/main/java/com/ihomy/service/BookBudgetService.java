package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.DictConst;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.BookBudgetDTO;
import com.ihomy.entity.BookBudget;
import com.ihomy.entity.BookRecord;
import com.ihomy.mapper.BookBudgetMapper;
import com.ihomy.mapper.BookRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 记账预算业务:按支出分类设一个月度预算,与所选月份的实际支出实时对比。
 *
 * 预算金额每月复用同一份(不按月存多行);已花/剩余/超支不落库,按明细汇总派生。
 * 预算为家庭共享数据,改删限创建人本人或家长,跨家庭一律当不存在处理。
 */
@Service
@RequiredArgsConstructor
public class BookBudgetService {

    private static final int MONEY_SCALE = 2;
    private static final int RATIO_SCALE = 4;

    private final BookBudgetMapper budgetMapper;
    private final BookRecordMapper bookMapper;

    /** 预算列表 + 所选月份的实际支出对比(month 缺省当月) */
    public Map<String, Object> list(Long familyId, String month) {
        YearMonth ym = (month == null || month.isBlank()) ? YearMonth.now() : YearMonth.parse(month);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();
        List<BookBudget> budgets = budgetMapper.selectList(new LambdaQueryWrapper<BookBudget>()
                .eq(BookBudget::getFamilyId, familyId)
                .orderByAsc(BookBudget::getCategory));
        List<BookRecord> records = bookMapper.selectList(new LambdaQueryWrapper<BookRecord>()
                .eq(BookRecord::getFamilyId, familyId)
                .between(BookRecord::getRecordDate, start, end));
        return compare(budgets, records);
    }

    /** 新增预算:分类去重(同家庭同名分类只允许一条),金额必须大于 0 */
    public BookBudget create(Long userId, Long familyId, BookBudgetDTO dto) {
        String category = requireCategory(dto.getCategory());
        requirePositive(dto.getAmount());
        requireCategoryFree(familyId, category, null);

        BookBudget budget = new BookBudget();
        budget.setFamilyId(familyId);
        budget.setCreatedBy(userId);
        budget.setCategory(category);
        budget.setAmount(dto.getAmount());
        budget.setNote(dto.getNote());
        budgetMapper.insert(budget);
        return budget;
    }

    /** 编辑预算:仅创建人本人或家长可改;分类改到别的已存在分类时拒绝 */
    public void update(Long id, Long familyId, Long userId, boolean isOwner, BookBudgetDTO dto) {
        BookBudget budget = require(id, familyId);
        if (!budget.getCreatedBy().equals(userId) && !isOwner) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        String category = requireCategory(dto.getCategory());
        requirePositive(dto.getAmount());
        requireCategoryFree(familyId, category, id);

        BookBudget patch = new BookBudget();
        patch.setId(id);
        patch.setCategory(category);
        patch.setAmount(dto.getAmount());
        patch.setNote(dto.getNote());
        budgetMapper.updateById(patch);
    }

    /** 删除预算:仅创建人本人或家长;预算只是额度,不牵连已有明细 */
    public void delete(Long id, Long familyId, Long userId, boolean isOwner) {
        BookBudget budget = require(id, familyId);
        if (!budget.getCreatedBy().equals(userId) && !isOwner) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        budgetMapper.deleteById(id);
    }

    private BookBudget require(Long id, Long familyId) {
        BookBudget budget = budgetMapper.selectById(id);
        if (budget == null || !budget.getFamilyId().equals(familyId)) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        return budget;
    }

    private void requireCategoryFree(Long familyId, String category, Long selfId) {
        Long count = budgetMapper.selectCount(new LambdaQueryWrapper<BookBudget>()
                .eq(BookBudget::getFamilyId, familyId)
                .eq(BookBudget::getCategory, category)
                .ne(selfId != null, BookBudget::getId, selfId));
        if (count != null && count > 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "该分类已有预算，请直接修改那一条");
        }
    }

    private static String requireCategory(String category) {
        String trimmed = category == null ? "" : category.trim();
        if (trimmed.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "请选择支出分类");
        }
        return trimmed;
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "预算金额必须大于 0");
        }
    }

    /**
     * 预算与实际支出对比(纯函数,便于单测):只统计所选月份的支出,转账与收入不参与。
     *
     * 每个分类给出预算/已花/剩余/超支额/进度比;进度比 > 1 即超支,按进度比倒序(超支在最前)。
     * total 只合计「有预算的分类」,未设预算的支出单独放进 unbudgeted,避免进度比被稀释。
     */
    static Map<String, Object> compare(List<BookBudget> budgets, List<BookRecord> records) {
        Map<String, BigDecimal> spentByCategory = new TreeMap<>();
        BigDecimal monthExpense = BigDecimal.ZERO;
        for (BookRecord r : records) {
            if (!DictConst.BOOK_EXPENSE.equals(DictConst.bookType(r.getType()))) continue;
            BigDecimal amount = r.getAmount() == null ? BigDecimal.ZERO : r.getAmount();
            String category = r.getCategory() == null || r.getCategory().isBlank() ? "其他" : r.getCategory();
            spentByCategory.merge(category, amount, BigDecimal::add);
            monthExpense = monthExpense.add(amount);
        }

        List<Map<String, Object>> items = new ArrayList<>();
        BigDecimal totalBudget = BigDecimal.ZERO;
        BigDecimal totalSpent = BigDecimal.ZERO;
        int overCount = 0;
        for (BookBudget b : budgets) {
            BigDecimal amount = money(b.getAmount());
            BigDecimal spent = money(spentByCategory.getOrDefault(b.getCategory(), BigDecimal.ZERO));
            boolean over = spent.compareTo(amount) > 0;
            if (over) overCount++;
            totalBudget = totalBudget.add(amount);
            totalSpent = totalSpent.add(spent);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", b.getId());
            row.put("category", b.getCategory());
            row.put("amount", amount);
            row.put("note", b.getNote());
            row.put("spent", spent);
            row.put("remaining", money(amount.subtract(spent)));
            row.put("excess", money(over ? spent.subtract(amount) : BigDecimal.ZERO));
            row.put("ratio", ratio(spent, amount));
            row.put("over", over);
            items.add(row);
        }
        items.sort(Comparator
                .comparing((Map<String, Object> m) -> (BigDecimal) m.get("ratio")).reversed()
                .thenComparing(m -> (String) m.get("category")));

        Map<String, Object> total = new LinkedHashMap<>();
        total.put("budget", money(totalBudget));
        total.put("spent", money(totalSpent));
        total.put("remaining", money(totalBudget.subtract(totalSpent)));
        total.put("ratio", ratio(totalSpent, totalBudget));
        total.put("over", totalSpent.compareTo(totalBudget) > 0);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", total);
        result.put("overCount", overCount);
        // 本月支出里没设预算的部分(有预算的分类已计入 total.spent)
        result.put("unbudgeted", money(monthExpense.subtract(totalSpent)));
        return result;
    }

    private static BigDecimal money(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal ratio(BigDecimal part, BigDecimal total) {
        if (total == null || total.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO.setScale(RATIO_SCALE);
        return part.divide(total, RATIO_SCALE, RoundingMode.HALF_UP);
    }
}
