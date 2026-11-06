package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.BookRecurringSchedule;
import com.ihomy.common.DictConst;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.BookRecurringDTO;
import com.ihomy.entity.BookAccount;
import com.ihomy.entity.BookRecord;
import com.ihomy.entity.BookRecurring;
import com.ihomy.entity.SysUser;
import com.ihomy.mapper.BookAccountMapper;
import com.ihomy.mapper.BookRecordMapper;
import com.ihomy.mapper.BookRecurringMapper;
import com.ihomy.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 记账周期模板业务:维护固定收支模板,并按天把到期期次生成为普通记账流水。
 *
 * 生成是幂等的:先以「已生成日」做条件更新抢占(WHERE last_generated_date = 旧值),
 * 抢到才插入流水,同一天重复跑(定时任务 + 手动触发 + 多实例)不会重复插入。
 * 日期推算唯一来源 common/BookRecurringSchedule(月度短月取月末、每年 2/29 平年取 2/28);
 * 用户主动补账(新建模板 / 立即生成)从起始日补齐全部历史期次,定时任务补跑最多回溯 90 天。
 * 模板为家庭共享数据,改删限创建人本人或家长。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookRecurringService {

    private final BookRecurringMapper recurringMapper;
    private final BookRecordMapper bookMapper;
    private final BookAccountMapper accountMapper;
    private final SysUserMapper sysUserMapper;
    /** 抢占 + 插流水必须同一事务:中途失败回滚「已生成日」,下次还能补回来 */
    private final TransactionOperations txOps;

    /** 模板列表 + 账户/创建人名 + 下次生成日(家庭内,按起始日排序) */
    public List<Map<String, Object>> list(Long familyId) {
        List<BookRecurring> rows = recurringMapper.selectList(new LambdaQueryWrapper<BookRecurring>()
                .eq(BookRecurring::getFamilyId, familyId)
                .orderByAsc(BookRecurring::getStartDate)
                .orderByAsc(BookRecurring::getId));
        if (rows.isEmpty()) return List.of();

        Map<Long, String> accountNames = accountNames(rows.stream()
                .map(BookRecurring::getAccountId).filter(Objects::nonNull).distinct().collect(Collectors.toList()));
        Map<Long, String> creators = sysUserMapper.selectBatchIds(rows.stream()
                        .map(BookRecurring::getCreatedBy).distinct().collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(SysUser::getId, SysUser::getNickname, (a, b) -> a));

        LocalDate today = LocalDate.now();
        List<Map<String, Object>> out = new ArrayList<>();
        for (BookRecurring r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("type", r.getType());
            m.put("amount", r.getAmount());
            m.put("category", r.getCategory());
            m.put("accountId", r.getAccountId());
            m.put("accountName", r.getAccountId() == null ? null
                    : accountNames.getOrDefault(r.getAccountId(), "已删除账户"));
            m.put("remark", r.getRemark());
            m.put("period", r.getPeriod());
            m.put("startDate", r.getStartDate());
            m.put("endDate", r.getEndDate());
            m.put("enabled", r.getEnabled());
            m.put("lastGeneratedDate", r.getLastGeneratedDate());
            m.put("nextDate", nextDate(r, today));
            m.put("createdBy", r.getCreatedBy());
            m.put("createdByName", creators.getOrDefault(r.getCreatedBy(), "未知成员"));
            out.add(m);
        }
        return out;
    }

    /**
     * 新建模板:只允许收支两种类型;账户必须是本家庭未删除账户;分类空按「其他」。
     * 保存后立即从起始日把已到期期次补成流水(用户主动补账,不套定时任务的 90 天窗口),
     * 否则起始日早于今天的模板要等到次日的定时任务才会有账目。
     *
     * @return id 与本次补记的流水笔数
     */
    @Transactional
    public Map<String, Object> create(Long userId, Long familyId, BookRecurringDTO dto) {
        BookRecurring r = new BookRecurring();
        r.setFamilyId(familyId);
        r.setCreatedBy(userId);
        applyForm(r, familyId, dto, true);
        r.setEnabled(1);
        recurringMapper.insert(r);
        int generated = generateOne(r, LocalDate.now(), BookRecurringSchedule.FULL_HISTORY);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", r.getId());
        out.put("generated", generated);
        return out;
    }

    /**
     * 编辑模板:仅创建人本人或可管理账本者;改动周期锚点后从新锚点续算(已生成日保留)。
     * 从未生成过任何期次的模板(旧版本遗留 / 起始日在未来)在保存时补齐历史,回到正常节奏。
     *
     * @return 本次补记的流水笔数(通常 0)
     */
    public int update(Long id, Long familyId, Long userId, boolean canManage, BookRecurringDTO dto) {
        BookRecurring r = require(id, familyId);
        if (!r.getCreatedBy().equals(userId) && !canManage) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        applyForm(r, familyId, dto, false);
        if (dto.getEnabled() != null) {
            r.setEnabled(normalizeEnabled(dto.getEnabled()));
        }
        recurringMapper.updateById(r);
        // endDate / accountId 可为空(清除结束日、改成不挂账户):updateById 默认跳过 null,必须显式 SET
        recurringMapper.update(null, new LambdaUpdateWrapper<BookRecurring>()
                .eq(BookRecurring::getId, id)
                .set(BookRecurring::getEndDate, r.getEndDate())
                .set(BookRecurring::getAccountId, r.getAccountId()));
        // 只有「从未生成过」才回补:已生成中的模板改金额只影响后续期次,不拿新金额重写历史
        if (r.getEnabled() != null && r.getEnabled() == 1 && r.getLastGeneratedDate() == null) {
            return generateOne(r, LocalDate.now(), BookRecurringSchedule.FULL_HISTORY);
        }
        return 0;
    }

    /** 暂停 / 恢复:恢复时不补暂停期间的期次(已生成日推到昨天,今天起继续) */
    public void setEnabled(Long id, Long familyId, Long userId, boolean canManage, Integer enabled) {
        BookRecurring r = require(id, familyId);
        if (!r.getCreatedBy().equals(userId) && !canManage) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        int value = normalizeEnabled(enabled);
        LambdaUpdateWrapper<BookRecurring> w = new LambdaUpdateWrapper<BookRecurring>()
                .eq(BookRecurring::getId, id)
                .set(BookRecurring::getEnabled, value);
        if (value == 1) {
            // 恢复:暂停期间不补账,把「已生成日」推到昨天,今天起的期次才生成
            LocalDate floor = LocalDate.now().minusDays(1);
            if (r.getLastGeneratedDate() == null || r.getLastGeneratedDate().isBefore(floor)) {
                w.set(BookRecurring::getLastGeneratedDate, floor);
            }
        }
        recurringMapper.update(null, w);
    }

    /** 删除模板:仅创建人本人或可管理账本者;已生成的流水保留(它们就是普通流水) */
    public void delete(Long id, Long familyId, Long userId, boolean canManage) {
        BookRecurring r = require(id, familyId);
        if (!r.getCreatedBy().equals(userId) && !canManage) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        recurringMapper.deleteById(id);
    }

    /**
     * 手动为单个家庭补生成一次(与定时任务同一实现,幂等),返回本次新增条数。
     * 用户主动触发,按模板起始日补齐全部历史期次,不套定时任务的 90 天窗口。
     */
    public int generateForFamily(Long familyId) {
        List<BookRecurring> rows = recurringMapper.selectList(new LambdaQueryWrapper<BookRecurring>()
                .eq(BookRecurring::getFamilyId, familyId)
                .eq(BookRecurring::getEnabled, 1));
        LocalDate today = LocalDate.now();
        int total = 0;
        for (BookRecurring r : rows) {
            total += generateOne(r, today, BookRecurringSchedule.FULL_HISTORY);
        }
        return total;
    }

    /** 每日 00:10 扫描启用中的模板,把到期期次落成普通流水;单条失败不影响其他模板 */
    @Scheduled(cron = "0 10 0 * * *")
    public void generateDaily() {
        List<BookRecurring> rows = recurringMapper.selectList(new LambdaQueryWrapper<BookRecurring>()
                .eq(BookRecurring::getEnabled, 1));
        LocalDate today = LocalDate.now();
        int total = 0;
        for (BookRecurring r : rows) {
            try {
                // 定时补跑只补最近 90 天:停机太久不把陈年旧账一次性灌进来
                total += generateOne(r, today, BookRecurringSchedule.MAX_BACKFILL_DAYS);
            } catch (Exception e) {
                log.error("周期账目生成失败, id={}", r.getId(), e);
            }
        }
        if (total > 0) {
            log.info("周期账目生成完成, 新增 {} 条", total);
        }
    }

    /**
     * 单个模板生成(幂等核心):先用「已生成日」条件更新抢占,抢到才插流水。
     *
     * 条件更新命中 0 行说明已被另一次执行抢走(或已暂停/删除),直接跳过。
     */
    private int generateOne(BookRecurring r, LocalDate today, int backfillDays) {
        BookRecurringSchedule.Occurrences occ = BookRecurringSchedule.due(
                r.getPeriod(), r.getStartDate(), r.getEndDate(), r.getLastGeneratedDate(), today, backfillDays);
        if (occ.isEmpty()) return 0;

        Integer generated = txOps.execute(status -> claimAndInsert(r, occ));
        return generated == null ? 0 : generated;
    }

    /** 同一事务内:条件更新抢到「已生成日」后才插流水,插失败整体回滚 */
    private int claimAndInsert(BookRecurring r, BookRecurringSchedule.Occurrences occ) {
        LocalDate oldLast = r.getLastGeneratedDate();
        LambdaUpdateWrapper<BookRecurring> claim = new LambdaUpdateWrapper<BookRecurring>()
                .eq(BookRecurring::getId, r.getId())
                .eq(BookRecurring::getEnabled, 1)
                .set(BookRecurring::getLastGeneratedDate, occ.lastGeneratedDate());
        if (oldLast == null) {
            claim.isNull(BookRecurring::getLastGeneratedDate);
        } else {
            claim.eq(BookRecurring::getLastGeneratedDate, oldLast);
        }
        if (recurringMapper.update(null, claim) == 0) return 0;

        for (LocalDate date : occ.dates()) {
            BookRecord record = new BookRecord();
            record.setFamilyId(r.getFamilyId());
            record.setType(r.getType());
            record.setAmount(r.getAmount());
            record.setCategory(r.getCategory());
            record.setRemark(r.getRemark());
            record.setRecordDate(date);
            record.setCreatedBy(r.getCreatedBy());
            record.setAccountId(r.getAccountId());
            bookMapper.insert(record);
        }
        return occ.dates().size();
    }

    /** 下次生成日(暂停/已过期返回 null):取「已生成日与昨天的较晚者」之后的第一个周期日 */
    private static LocalDate nextDate(BookRecurring r, LocalDate today) {
        if (r.getEnabled() == null || r.getEnabled() != 1) return null;
        LocalDate anchor = r.getLastGeneratedDate();
        LocalDate floor = today.minusDays(1);
        LocalDate after = anchor == null || anchor.isBefore(floor) ? floor : anchor;
        return BookRecurringSchedule.nextAfter(r.getPeriod(), r.getStartDate(), r.getEndDate(), after);
    }

    /** 表单落到实体(新建全量、编辑只覆盖提交项);类型只允许收支,账户校验归属 */
    private void applyForm(BookRecurring r, Long familyId, BookRecurringDTO dto, boolean creating) {
        String type = dto.getType() == null || dto.getType().isBlank()
                ? (creating ? DictConst.BOOK_EXPENSE : r.getType())
                : DictConst.bookType(dto.getType());
        if (DictConst.BOOK_TRANSFER.equals(type)) {
            throw new BizException(ResultCode.BAD_REQUEST, "周期模板只支持支出和收入");
        }
        r.setType(type);
        if (dto.getAmount() != null) {
            if (dto.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException(ResultCode.BAD_REQUEST, "金额必须大于 0");
            }
            r.setAmount(dto.getAmount());
        }
        if (creating || dto.getCategory() != null) {
            r.setCategory(dto.getCategory() == null || dto.getCategory().isBlank()
                    ? "其他" : dto.getCategory().trim());
        }
        if (creating || dto.getRemark() != null) {
            r.setRemark(dto.getRemark());
        }
        // 账户可清空(改成不挂账户):表单整体提交,空值即清空
        r.setAccountId(dto.getAccountId());
        if (dto.getAccountId() != null) {
            requireOwnedAccount(familyId, dto.getAccountId());
        }
        if (dto.getPeriod() != null) {
            r.setPeriod(BookRecurringSchedule.normalizePeriod(dto.getPeriod()));
        } else if (creating) {
            r.setPeriod(BookRecurringSchedule.PERIOD_MONTHLY);
        }
        if (dto.getStartDate() != null) {
            r.setStartDate(dto.getStartDate());
        }
        if (dto.getEndDate() != null) {
            r.setEndDate(dto.getEndDate());
        }
        if (r.getStartDate() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "请选择起始日期");
        }
        if (r.getEndDate() != null && r.getEndDate().isBefore(r.getStartDate())) {
            throw new BizException(ResultCode.BAD_REQUEST, "结束日期不能早于起始日期");
        }
        if (r.getAmount() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "请填写金额");
        }
    }

    private void requireOwnedAccount(Long familyId, Long accountId) {
        BookAccount account = accountMapper.selectById(accountId);
        if (account == null || !Objects.equals(account.getFamilyId(), familyId)) {
            throw new BizException(ResultCode.BAD_REQUEST, "所选账户已失效，请重新选择");
        }
    }

    private BookRecurring require(Long id, Long familyId) {
        BookRecurring r = recurringMapper.selectById(id);
        if (r == null || !r.getFamilyId().equals(familyId)) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        return r;
    }

    private Map<Long, String> accountNames(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        Set<Long> wanted = new LinkedHashSet<>(ids);
        return accountMapper.selectBatchIds(wanted).stream()
                .collect(Collectors.toMap(BookAccount::getId, BookAccount::getName, (a, b) -> a));
    }

    private static int normalizeEnabled(Integer enabled) {
        return enabled != null && enabled == 0 ? 0 : 1;
    }
}
