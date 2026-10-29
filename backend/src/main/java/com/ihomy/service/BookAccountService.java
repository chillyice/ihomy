package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.DictConst;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.BookAccountDTO;
import com.ihomy.entity.BookAccount;
import com.ihomy.entity.BookRecord;
import com.ihomy.entity.SysUser;
import com.ihomy.mapper.BookAccountMapper;
import com.ihomy.mapper.BookRecordMapper;
import com.ihomy.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 记账账户业务:现金/银行卡/支付宝等账户的增删改查与实时余额。
 *
 * 余额不落库,按「期初余额 + 收入 − 支出 − 转出 + 转入」实时算(与前端 utils/bookLedger.js 同一口径);
 * 账户为家庭共享数据,改删限创建人本人或家长,跨家庭一律当不存在处理。
 */
@Service
@RequiredArgsConstructor
public class BookAccountService {

    private final BookAccountMapper accountMapper;
    private final BookRecordMapper bookMapper;
    private final SysUserMapper sysUserMapper;

    /** 账户列表 + 实时余额 + 记录数(余额条/记账弹窗/总账资金分布共用) */
    public List<Map<String, Object>> list(Long familyId) {
        List<BookAccount> accounts = accountMapper.selectList(new LambdaQueryWrapper<BookAccount>()
                .eq(BookAccount::getFamilyId, familyId)
                .orderByAsc(BookAccount::getSortOrder)
                .orderByAsc(BookAccount::getId));
        if (accounts.isEmpty()) return List.of();

        // ponytail: 家庭账本量级小(≤ 万行),一次取回内存聚合最简单;超过 5 万行改 SQL GROUP BY 按账户汇总
        List<BookRecord> records = bookMapper.selectList(new LambdaQueryWrapper<BookRecord>()
                .eq(BookRecord::getFamilyId, familyId));
        Map<Long, AccountSum> sums = sumByAccount(records);

        Map<Long, String> names = sysUserMapper.selectBatchIds(accounts.stream()
                        .map(BookAccount::getCreatedBy).distinct().collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(SysUser::getId, SysUser::getNickname, (a, b) -> a));

        return accounts.stream().map(a -> {
            AccountSum sum = sums.getOrDefault(a.getId(), AccountSum.ZERO);
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", a.getId());
            map.put("name", a.getName());
            map.put("accountType", a.getAccountType());
            map.put("institution", a.getInstitution());
            map.put("cardTail", a.getCardTail());
            map.put("initialBalance", nullToZero(a.getInitialBalance()));
            map.put("balance", sum.balance(nullToZero(a.getInitialBalance())));
            map.put("income", sum.income());
            map.put("expense", sum.expense());
            map.put("transferIn", sum.transferIn());
            map.put("transferOut", sum.transferOut());
            map.put("count", sum.count());
            map.put("sortOrder", a.getSortOrder());
            map.put("note", a.getNote());
            map.put("createdBy", a.getCreatedBy());
            map.put("createdByName", names.getOrDefault(a.getCreatedBy(), "未知成员"));
            return map;
        }).collect(Collectors.toList());
    }

    /** 新建账户:类型归一、期初与排序空补 0 */
    public BookAccount create(Long userId, Long familyId, BookAccountDTO dto) {
        BookAccount account = new BookAccount();
        account.setFamilyId(familyId);
        account.setCreatedBy(userId);
        account.setName(dto.getName().trim());
        account.setAccountType(DictConst.bookAccountType(dto.getAccountType()));
        account.setInstitution(dto.getInstitution());
        account.setCardTail(dto.getCardTail());
        account.setInitialBalance(nullToZero(dto.getInitialBalance()));
        account.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        account.setNote(dto.getNote());
        accountMapper.insert(account);
        return account;
    }

    /** 编辑账户:仅创建人本人或家长可改,只覆盖提交了的字段 */
    public void update(Long id, Long familyId, Long userId, boolean isOwner, BookAccountDTO dto) {
        BookAccount account = require(id, familyId);
        if (!account.getCreatedBy().equals(userId) && !isOwner) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        BookAccount patch = new BookAccount();
        boolean touched = false;
        if (dto.getName() != null) {
            patch.setName(dto.getName().trim());
            touched = true;
        }
        if (dto.getAccountType() != null) {
            patch.setAccountType(DictConst.bookAccountType(dto.getAccountType()));
            touched = true;
        }
        if (dto.getInstitution() != null) {
            patch.setInstitution(dto.getInstitution());
            touched = true;
        }
        if (dto.getCardTail() != null) {
            patch.setCardTail(dto.getCardTail());
            touched = true;
        }
        if (dto.getInitialBalance() != null) {
            patch.setInitialBalance(dto.getInitialBalance());
            touched = true;
        }
        if (dto.getSortOrder() != null) {
            patch.setSortOrder(dto.getSortOrder());
            touched = true;
        }
        if (dto.getNote() != null) {
            patch.setNote(dto.getNote());
            touched = true;
        }
        if (!touched) return;
        patch.setId(id);
        // 实体 updatedAt 是 FieldStrategy.NEVER,updateById 不会回写旧值,交给 DB 的 ON UPDATE CURRENT_TIMESTAMP
        accountMapper.updateById(patch);
    }

    /** 删除账户:有记账记录引用时拒绝,不静默把记录置空 */
    public void delete(Long id, Long familyId, Long userId, boolean isOwner) {
        BookAccount account = require(id, familyId);
        if (!account.getCreatedBy().equals(userId) && !isOwner) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        Long refs = bookMapper.selectCount(new LambdaQueryWrapper<BookRecord>()
                .eq(BookRecord::getFamilyId, familyId)
                .and(w -> w.eq(BookRecord::getAccountId, id)
                        .or().eq(BookRecord::getFromAccountId, id)
                        .or().eq(BookRecord::getToAccountId, id)));
        if (refs != null && refs > 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "该账户下还有 " + refs + " 笔记录，请先调整记录归属");
        }
        accountMapper.deleteById(id);
    }

    private BookAccount require(Long id, Long familyId) {
        BookAccount account = accountMapper.selectById(id);
        if (account == null || !account.getFamilyId().equals(familyId)) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        return account;
    }

    private static BigDecimal nullToZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** 按账户汇总收支与转账(纯函数,便于单测);未挂账户的记录不进入结果 */
    static Map<Long, AccountSum> sumByAccount(List<BookRecord> records) {
        Map<Long, AccountSum> sums = new LinkedHashMap<>();
        for (BookRecord r : records) {
            BigDecimal amount = nullToZero(r.getAmount());
            if (DictConst.BOOK_INCOME.equals(r.getType()) && r.getAccountId() != null) {
                sums.merge(r.getAccountId(), new AccountSum(amount, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 1),
                        AccountSum::plus);
            } else if (DictConst.BOOK_EXPENSE.equals(r.getType()) && r.getAccountId() != null) {
                sums.merge(r.getAccountId(), new AccountSum(BigDecimal.ZERO, amount, BigDecimal.ZERO, BigDecimal.ZERO, 1),
                        AccountSum::plus);
            } else if (DictConst.BOOK_TRANSFER.equals(r.getType())) {
                if (r.getFromAccountId() != null) {
                    sums.merge(r.getFromAccountId(),
                            new AccountSum(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, amount, 1),
                            AccountSum::plus);
                }
                if (r.getToAccountId() != null) {
                    sums.merge(r.getToAccountId(),
                            new AccountSum(BigDecimal.ZERO, BigDecimal.ZERO, amount, BigDecimal.ZERO, 1),
                            AccountSum::plus);
                }
            }
        }
        return sums;
    }

    /** 单账户流水累计:income/expense 为收支,transferIn/transferOut 为转入转出,count 为相关记录笔数 */
    record AccountSum(BigDecimal income, BigDecimal expense, BigDecimal transferIn, BigDecimal transferOut, int count) {

        static final AccountSum ZERO = new AccountSum(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0);

        AccountSum plus(AccountSum other) {
            return new AccountSum(income.add(other.income), expense.add(other.expense),
                    transferIn.add(other.transferIn), transferOut.add(other.transferOut), count + other.count);
        }

        BigDecimal balance(BigDecimal initialBalance) {
            return initialBalance.add(income).subtract(expense).subtract(transferOut).add(transferIn);
        }
    }
}
