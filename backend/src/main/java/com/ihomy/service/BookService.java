package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.BookLedgerUtil;
import com.ihomy.common.DictConst;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.BookDTO;
import com.ihomy.entity.BookAccount;
import com.ihomy.entity.BookRecord;
import com.ihomy.entity.SysUser;
import com.ihomy.mapper.BookAccountMapper;
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
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 记账业务:按月查询明细与统计(收入/支出/结余/分类汇总),
 * 收支金额用 BIGDECIMAL 累加避免浮点误差。只取单月数据内存汇总(家庭规模小)。
 * V10.78 起明细可挂账户:收支记 account_id,转账记 from/to 账户,账户归属在本层校验。
 */
@Service
@RequiredArgsConstructor
public class BookService {

    private final BookRecordMapper bookMapper;
    private final SysUserMapper sysUserMapper;
    private final BookAccountMapper bookAccountMapper;

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
        // 记录人昵称与账户名各批量查一次(禁 N+1)
        Map<Long, String> names = Map.of();
        if (!records.isEmpty()) {
            names = sysUserMapper.selectBatchIds(records.stream()
                            .map(BookRecord::getCreatedBy).distinct().collect(Collectors.toList()))
                    .stream().collect(Collectors.toMap(SysUser::getId, SysUser::getNickname, (a, b) -> a));
        }
        Map<Long, String> accountNames = accountNames(records.stream()
                .flatMap(r -> Stream.of(r.getAccountId(), r.getFromAccountId(), r.getToAccountId()))
                .filter(Objects::nonNull).distinct().collect(Collectors.toList()));
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
            m.put("accountId", r.getAccountId());
            m.put("fromAccountId", r.getFromAccountId());
            m.put("toAccountId", r.getToAccountId());
            m.put("accountName", accountNameOf(r.getAccountId(), accountNames));
            m.put("fromAccountName", accountNameOf(r.getFromAccountId(), accountNames));
            m.put("toAccountName", accountNameOf(r.getToAccountId(), accountNames));
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

    /** 本月收支摘要(供首页卡片,轻量查询);familyId 为 null(游客,本接口对游客放行)时返回空摘要 */
    public Map<String, Object> summary(Long familyId) {
        LocalDate start = YearMonth.now().atDay(1);
        LocalDate end = YearMonth.now().atEndOfMonth();
        List<BookRecord> records = familyId == null ? List.of()
                : bookMapper.selectList(new LambdaQueryWrapper<BookRecord>()
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

    /** 总账汇总:全时段累计 + 可选年份范围的逐月滚动;成员名在此批量回填(聚合本身是纯函数) */
    @SuppressWarnings("unchecked")
    public Map<String, Object> ledger(Long familyId, Integer year) {
        // ponytail: 与账户余额同一口径,家庭量级一次取回内存汇总;单家庭超过 5 万行改 SQL GROUP BY
        List<BookRecord> records = bookMapper.selectList(new LambdaQueryWrapper<BookRecord>()
                .eq(BookRecord::getFamilyId, familyId)
                .orderByAsc(BookRecord::getRecordDate)
                .orderByAsc(BookRecord::getId));
        Map<String, Object> ledger = BookLedgerUtil.aggregate(records, year);

        List<Map<String, Object>> members = (List<Map<String, Object>>) ledger.get("byMember");
        if (members != null && !members.isEmpty()) {
            List<Long> userIds = members.stream().map(m -> (Long) m.get("userId")).distinct().collect(Collectors.toList());
            Map<Long, String> names = sysUserMapper.selectBatchIds(userIds).stream()
                    .collect(Collectors.toMap(SysUser::getId,
                            u -> u.getNickname() != null && !u.getNickname().isBlank() ? u.getNickname() : u.getUsername(),
                            (a, b) -> a));
            for (Map<String, Object> m : members) {
                m.put("name", names.getOrDefault((Long) m.get("userId"), "未知成员"));
            }
        }
        return ledger;
    }

    /** 记一笔(缺省分类"其他",金额必须大于 0);账户按类型定形并校验归属 */
    public BookRecord create(Long userId, Long familyId, BookDTO dto) {
        if (dto.getAmount() == null || dto.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "金额必须大于 0");
        }
        String type = DictConst.bookType(dto.getType());
        AccountRefs refs = normalizeAccounts(type, dto.getAccountId(), dto.getFromAccountId(), dto.getToAccountId());
        requireOwnedAccounts(familyId, refs);

        BookRecord r = new BookRecord();
        r.setFamilyId(familyId);
        r.setType(type);
        r.setAmount(dto.getAmount());
        r.setCategory(dto.getCategory() == null || dto.getCategory().isBlank()
                ? "其他" : dto.getCategory());
        r.setRemark(dto.getRemark());
        r.setRecordDate(dto.getRecordDate() == null ? LocalDate.now() : dto.getRecordDate());
        r.setCreatedBy(userId);
        r.setAccountId(refs.accountId());
        r.setFromAccountId(refs.fromAccountId());
        r.setToAccountId(refs.toAccountId());
        bookMapper.insert(r);
        return r;
    }

    /** 改账:仅记录人本人或家长可改,且须同一家庭;账户按「最终类型 + 覆盖后的账户列」整体校验 */
    public void update(Long id, Long familyId, Long userId, boolean isOwner, BookDTO dto) {
        BookRecord r = require(id, familyId);
        if (!r.getCreatedBy().equals(userId) && !isOwner) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        String type = dto.getType() != null ? DictConst.bookType(dto.getType()) : r.getType();
        AccountRefs refs = normalizeAccounts(type,
                dto.getAccountId() != null ? dto.getAccountId() : r.getAccountId(),
                dto.getFromAccountId() != null ? dto.getFromAccountId() : r.getFromAccountId(),
                dto.getToAccountId() != null ? dto.getToAccountId() : r.getToAccountId());
        requireOwnedAccounts(familyId, refs);

        r.setType(type);
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
        // 账户三列必须显式 set:updateById 默认跳过 null,把转账改回收支时旧 from/to 会残留下来
        bookMapper.update(null, new LambdaUpdateWrapper<BookRecord>()
                .eq(BookRecord::getId, id)
                .set(BookRecord::getAccountId, refs.accountId())
                .set(BookRecord::getFromAccountId, refs.fromAccountId())
                .set(BookRecord::getToAccountId, refs.toAccountId()));
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

    /**
     * 账户三列按类型定形(纯函数):收支只认 account_id,转账只认转出/转入且不能是同一边;
     * 不做归属校验(需要查库,交给 requireOwnedAccounts)。
     */
    static AccountRefs normalizeAccounts(String type, Long accountId, Long fromAccountId, Long toAccountId) {
        if (DictConst.BOOK_TRANSFER.equals(type)) {
            if (fromAccountId == null && toAccountId == null) {
                throw new BizException(ResultCode.BAD_REQUEST, "转账请至少选择转出或转入账户");
            }
            if (fromAccountId != null && fromAccountId.equals(toAccountId)) {
                throw new BizException(ResultCode.BAD_REQUEST, "转出与转入账户不能是同一个");
            }
            return new AccountRefs(null, fromAccountId, toAccountId);
        }
        return new AccountRefs(accountId, null, null);
    }

    /** 校验被引用的账户都是本家庭且未删除(一次批量查);已删除的账户查不到,同样按失效处理 */
    private void requireOwnedAccounts(Long familyId, AccountRefs refs) {
        List<Long> ids = Stream.of(refs.accountId(), refs.fromAccountId(), refs.toAccountId())
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) return;
        checkAccountsOwned(familyId, ids, bookAccountMapper.selectBatchIds(ids));
    }

    /** 纯判定:查到的账户必须覆盖全部引用且都属本家庭 */
    static void checkAccountsOwned(Long familyId, List<Long> wanted, List<BookAccount> found) {
        boolean owned = found.size() == wanted.size()
                && found.stream().allMatch(a -> Objects.equals(a.getFamilyId(), familyId));
        if (!owned) {
            throw new BizException(ResultCode.BAD_REQUEST, "所选账户已失效，请重新选择");
        }
    }

    /** 一次批量查账户名,id→名称;已删除账户查不到,回填时提示「已删除账户」 */
    private Map<Long, String> accountNames(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return bookAccountMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(BookAccount::getId, BookAccount::getName, (a, b) -> a));
    }

    private static String accountNameOf(Long id, Map<Long, String> names) {
        if (id == null) return null;
        return names.getOrDefault(id, "已删除账户");
    }

    /** 明细上的账户三列:收支只填 accountId,转账只填 from/to */
    record AccountRefs(Long accountId, Long fromAccountId, Long toAccountId) {
    }
}