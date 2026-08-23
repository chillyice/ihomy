package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.DictConst;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.BookDTO;
import com.ihomy.entity.BookRecord;
import com.ihomy.entity.SysUser;
import com.ihomy.mapper.BookRecordMapper;
import com.ihomy.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 记账业务:按月查询明细与统计(收入/支出/结余/分类汇总),
 * 收支金额用 BIGDECIMAL 累加避免浮点误差。只取单月数据内存汇总(家庭规模小)。
 */
@Service
@RequiredArgsConstructor
public class BookService {

    private final BookRecordMapper bookMapper;
    private final SysUserMapper sysUserMapper;

    /** 按月查询明细+统计,month 形如 2026-07(缺省当月) */
    public Map<String, Object> list(Long familyId, String month) {
        LocalDate start = (month == null || month.isBlank())
                ? YearMonth.now().atDay(1)
                : YearMonth.parse(month).atDay(1);
        LocalDate end = YearMonth.from(start).atEndOfMonth();
        List<BookRecord> records = bookMapper.selectList(new LambdaQueryWrapper<BookRecord>()
                .eq(BookRecord::getFamilyId, familyId)
                .between(BookRecord::getRecordDate, start, end)
                .orderByDesc(BookRecord::getRecordDate)
                .orderByDesc(BookRecord::getId));

        // 统计:收入/支出/结余(转账不进结余)
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        Map<String, BigDecimal> expenseByCat = new LinkedHashMap<>();
        for (BookRecord r : records) {
            if (DictConst.BOOK_INCOME.equals(r.getType())) {
                income = income.add(r.getAmount());
            } else if (DictConst.BOOK_EXPENSE.equals(r.getType())) {
                expense = expense.add(r.getAmount());
                expenseByCat.merge(r.getCategory(), r.getAmount(), BigDecimal::add);
            }
        }

        Map<String, Object> result = new HashMap<>();
        // 记录人昵称(批量一次查)
        Map<Long, String> names = Map.of();
        if (!records.isEmpty()) {
            names = sysUserMapper.selectBatchIds(records.stream()
                            .map(BookRecord::getCreatedBy).distinct().collect(Collectors.toList()))
                    .stream().collect(Collectors.toMap(SysUser::getId, SysUser::getNickname));
        }
        List<Map<String, Object>> views = new ArrayList<>();
        for (BookRecord r : records) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", r.getId());
            m.put("type", r.getType());
            m.put("amount", r.getAmount());
            m.put("category", r.getCategory());
            m.put("remark", r.getRemark());
            m.put("recordDate", r.getRecordDate());
            m.put("createdBy", r.getCreatedBy());
            m.put("creatorName", names.getOrDefault(r.getCreatedBy(), "未知成员"));
            views.add(m);
        }
        result.put("records", views);
        Map<String, Object> stats = new HashMap<>();
        stats.put("income", income);
        stats.put("expense", expense);
        stats.put("balance", income.subtract(expense));
        stats.put("categoryStats", expenseByCat.entrySet().stream()
                .map(e -> Map.of("category", e.getKey(), "total", e.getValue()))
                .collect(Collectors.toList()));
        result.put("stats", stats);
        return result;
    }

    /** 本月收支摘要(供首页卡片,轻量查询) */
    public Map<String, Object> summary(Long familyId) {
        LocalDate start = YearMonth.now().atDay(1);
        LocalDate end = YearMonth.now().atEndOfMonth();
        List<BookRecord> records = bookMapper.selectList(new LambdaQueryWrapper<BookRecord>()
                .eq(BookRecord::getFamilyId, familyId)
                .between(BookRecord::getRecordDate, start, end));
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        for (BookRecord r : records) {
            if (DictConst.BOOK_INCOME.equals(r.getType())) income = income.add(r.getAmount());
            else if (DictConst.BOOK_EXPENSE.equals(r.getType())) expense = expense.add(r.getAmount());
        }
        Map<String, Object> result = new HashMap<>();
        result.put("month", start.toString().substring(0, 7));
        result.put("income", income);
        result.put("expense", expense);
        result.put("balance", income.subtract(expense));
        result.put("count", records.size());
        return result;
    }

    /** 记一笔(缺省分类"其他",金额必须大于 0) */
    public BookRecord create(Long userId, Long familyId, BookDTO dto) {
        if (dto.getAmount() == null || dto.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "金额必须大于 0");
        }
        BookRecord r = new BookRecord();
        r.setFamilyId(familyId);
        r.setType(DictConst.bookType(dto.getType()));
        r.setAmount(dto.getAmount());
        r.setCategory(dto.getCategory() == null || dto.getCategory().isBlank()
                ? "其他" : dto.getCategory());
        r.setRemark(dto.getRemark());
        r.setRecordDate(dto.getRecordDate() == null ? LocalDate.now() : dto.getRecordDate());
        r.setCreatedBy(userId);
        bookMapper.insert(r);
        return r;
    }

    /** 改账:仅记录人本人或家长可改,且须同一家庭 */
    public void update(Long id, Long familyId, Long userId, boolean isOwner, BookDTO dto) {
        BookRecord r = require(id, familyId);
        if (!r.getCreatedBy().equals(userId) && !isOwner) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        if (dto.getType() != null) r.setType(DictConst.bookType(dto.getType()));
        if (dto.getAmount() != null) {
            if (dto.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException(ResultCode.BAD_REQUEST, "金额必须大于 0");
            }
            r.setAmount(dto.getAmount());
        }
        if (dto.getCategory() != null) r.setCategory(dto.getCategory());
        if (dto.getRemark() != null) r.setRemark(dto.getRemark());
        if (dto.getRecordDate() != null) r.setRecordDate(dto.getRecordDate());
        bookMapper.updateById(r);
    }

    /** 删除账目:仅记录人本人或家长 */
    public void delete(Long id, Long familyId, Long userId, boolean isOwner) {
        BookRecord r = require(id, familyId);
        if (!r.getCreatedBy().equals(userId) && !isOwner) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        bookMapper.deleteById(id);
    }

    private BookRecord require(Long id, Long familyId) {
        BookRecord r = bookMapper.selectById(id);
        if (r == null || !r.getFamilyId().equals(familyId)) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        return r;
    }
}