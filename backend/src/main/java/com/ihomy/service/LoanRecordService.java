package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.DictConst;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.FamilyLoanDTO;
import com.ihomy.dto.LoanRepaySyncDTO;
import com.ihomy.entity.BookAccount;
import com.ihomy.entity.BookRecord;
import com.ihomy.entity.FamilyLoan;
import com.ihomy.entity.FamilyLoanEvent;
import com.ihomy.entity.FamilyLoanRepay;
import com.ihomy.entity.SysUser;
import com.ihomy.mapper.BookAccountMapper;
import com.ihomy.mapper.BookRecordMapper;
import com.ihomy.mapper.FamilyLoanEventMapper;
import com.ihomy.mapper.FamilyLoanMapper;
import com.ihomy.mapper.FamilyLoanRepayMapper;
import com.ihomy.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 家庭贷款记录业务:登记真实贷款与事件时间轴(利率调整/提前还款/换还款账户),还款流水由前端按事件重算
 * (utils/loan.js 的 loanLedger,口径与银行一致:利息 = 剩余本金 × 月利率逐期取整到分、末期结清)。
 *
 * 贷款为家庭共享数据,全员可见;编辑删除不限创建人(持 loan:manage 即可,照家庭账目口径)。
 * 事件在编辑时整体重写(物理删后重建),不设逻辑删。
 *
 * V11.43 绑定还款账户:贷款可挂一个记账账户,已到期期次自动补记为该账户的支出(记账本)。
 * 月供金额唯一来源仍是前端流水引擎,后端不复算月供,只按期次幂等补插并校验归属;
 * 「第 N 期用哪个账户」的规则在前端 utils/loan.js(loanAccountOfPeriod),后端只校验账户属于该贷款配置过的账户。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoanRecordService {

    private final FamilyLoanMapper familyLoanMapper;
    private final FamilyLoanEventMapper familyLoanEventMapper;
    private final FamilyLoanRepayMapper familyLoanRepayMapper;
    private final BookAccountMapper bookAccountMapper;
    private final BookRecordMapper bookRecordMapper;
    private final SysUserMapper sysUserMapper;

    private static final Set<String> CHANNELS = Set.of("COMMERCIAL", "FUND", "OTHER");
    private static final Set<String> METHODS = Set.of("EQUAL_INSTALLMENT", "EQUAL_PRINCIPAL");
    private static final Set<String> EVENT_TYPES = Set.of("RATE_CHANGE", "PREPAY", "ACCOUNT_CHANGE");
    private static final Set<String> PREPAY_STRATEGIES = Set.of("SHORTEN", "REDUCE");

    private static final int MAX_MONTHS = 1200;
    /** 年利率上限与 rate 列 DECIMAL(6,4) 对齐:填 100 或第五位小数四舍五入进位都会落库越界走 500 */
    private static final BigDecimal MAX_RATE = new BigDecimal("99.9999");
    /** 自动记账的分类名(记账本按分类汇总时一眼认出是月供) */
    private static final String REPAY_CATEGORY = "贷款还款";

    /** 贷款列表(含各自的事件时间轴),创建人昵称与账户名批量回填(不做 N+1) */
    public List<Map<String, Object>> list(Long familyId) {
        List<FamilyLoan> loans = familyLoanMapper.selectList(new LambdaQueryWrapper<FamilyLoan>()
                .eq(FamilyLoan::getFamilyId, familyId)
                .orderByDesc(FamilyLoan::getCreatedAt));
        if (loans.isEmpty()) return List.of();

        List<Long> loanIds = loans.stream().map(FamilyLoan::getId).collect(Collectors.toList());
        List<FamilyLoanEvent> events = familyLoanEventMapper.selectList(
                new LambdaQueryWrapper<FamilyLoanEvent>().in(FamilyLoanEvent::getLoanId, loanIds)
                        // 同期次事件按 id 定序:事件序决定前端 loanLedger 重算结果,必须全客户端一致
                        .orderByAsc(FamilyLoanEvent::getEffectivePeriod, FamilyLoanEvent::getId));

        List<Long> accountIds = new ArrayList<>();
        loans.forEach(l -> accountIds.add(l.getAccountId()));
        events.forEach(e -> accountIds.add(e.getAccountId()));
        Map<Long, String> accountNames = accountNames(accountIds);

        Map<Long, List<Integer>> bookedPeriods = bookedPeriods(familyId);
        Map<Long, String> names = sysUserMapper.selectBatchIds(
                        loans.stream().map(FamilyLoan::getCreatedBy).distinct().collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(SysUser::getId,
                        u -> u.getNickname() != null ? u.getNickname() : u.getUsername(), (a, b) -> a));

        Map<Long, List<Map<String, Object>>> eventsByLoan = events.stream()
                .collect(Collectors.groupingBy(FamilyLoanEvent::getLoanId,
                        Collectors.mapping(e -> toEventMap(e, accountNames), Collectors.toList())));

        return loans.stream().map(l -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", l.getId());
            map.put("name", l.getName());
            map.put("channel", l.getChannel());
            map.put("method", l.getMethod());
            map.put("amount", l.getAmount());
            map.put("months", l.getMonths());
            map.put("loanDate", l.getLoanDate());
            map.put("firstPayDate", l.getFirstPayDate());
            map.put("firstPayment", l.getFirstPayment());
            map.put("rate", l.getRate());
            map.put("accountId", l.getAccountId());
            map.put("accountName", accountName(l.getAccountId(), accountNames));
            map.put("groupName", l.getGroupName());
            map.put("note", l.getNote());
            map.put("createdBy", l.getCreatedBy());
            map.put("createdByName", names.getOrDefault(l.getCreatedBy(), "未知成员"));
            map.put("createdAt", l.getCreatedAt());
            map.put("updatedAt", l.getUpdatedAt());
            map.put("bookedPeriods", bookedPeriods.getOrDefault(l.getId(), List.of()));
            map.put("events", eventsByLoan.getOrDefault(l.getId(), List.of()));
            return map;
        }).collect(Collectors.toList());
    }

    @Transactional
    public Long create(Long userId, Long familyId, FamilyLoanDTO dto) {
        FamilyLoan loan = new FamilyLoan();
        loan.setFamilyId(familyId);
        loan.setCreatedBy(userId);
        applyForm(loan, dto);
        foldInitialAccountChange(familyId, loan, dto);
        familyLoanMapper.insert(loan);
        insertEvents(loan.getId(), familyId, dto);
        return loan.getId();
    }

    /** 编辑贷款:基本信息与事件时间轴整体替换 */
    @Transactional
    public void update(Long id, Long familyId, FamilyLoanDTO dto) {
        FamilyLoan loan = require(id, familyId);
        applyForm(loan, dto);
        foldInitialAccountChange(familyId, loan, dto);
        // 只 SET 业务字段,避免 updateById 回写旧 updated_at 抑制 ON UPDATE CURRENT_TIMESTAMP
        LambdaUpdateWrapper<FamilyLoan> uw = new LambdaUpdateWrapper<FamilyLoan>()
                .eq(FamilyLoan::getId, id)
                .set(FamilyLoan::getName, loan.getName())
                .set(FamilyLoan::getChannel, loan.getChannel())
                .set(FamilyLoan::getMethod, loan.getMethod())
                .set(FamilyLoan::getAmount, loan.getAmount())
                .set(FamilyLoan::getMonths, loan.getMonths())
                .set(FamilyLoan::getLoanDate, loan.getLoanDate())
                .set(FamilyLoan::getFirstPayDate, loan.getFirstPayDate())
                .set(FamilyLoan::getFirstPayment, loan.getFirstPayment())
                .set(FamilyLoan::getRate, loan.getRate())
                // account_id 必须显式 set:updateById 默认跳过 null,解绑账户会残留旧值
                .set(FamilyLoan::getAccountId, loan.getAccountId())
                .set(FamilyLoan::getGroupName, loan.getGroupName())
                .set(FamilyLoan::getNote, loan.getNote());
        familyLoanMapper.update(null, uw);
        familyLoanEventMapper.deleteByLoanId(id);
        insertEvents(id, familyId, dto);
    }

    @Transactional
    public void delete(Long id, Long familyId) {
        require(id, familyId);
        familyLoanMapper.deleteById(id);
        familyLoanEventMapper.deleteByLoanId(id);
    }

    /**
     * 贷款还款记账同步:前端按流水给出「已到期 + 该期有还款账户」的期次(含已记账的),
     * 这里按 (loan_id, period) 幂等补插,并把换账户后的既有账目迁到新账户。
     *
     * 已记过的期次不重复生成(含用户在记账本删掉流水的情况:台账还在,不再重复生成),但**金额/还款日/
     * 账户都会按最新贷款信息校正**——改了利率、期限、首期还款日,或补录了利率调整/提前还款后,已记进
     * 记账本的历史月供也要跟着变,否则贷款页的流水表与记账本里的支出对不上(月供金额唯一计算源仍是
     * 前端 `utils/loan.js`,这里只落库不重算)。
     * 两条保护:①用户在记账本手动改过该笔流水(金额/日期/账户任一与台账快照不一致)时整条保留用户选择;
     * ②`(贷款, 期次)` 唯一键保证同一期不会重复生成。
     *
     * @return created=本次新生成的记账笔数,moved=改挂新账户的已记账笔数,adjusted=按最新计划重算金额/日期的流水笔数
     */
    @Transactional
    public Map<String, Object> syncRepays(Long familyId, LoanRepaySyncDTO dto) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("created", 0);
        out.put("moved", 0);
        out.put("adjusted", 0);
        List<LoanRepaySyncDTO.Item> items = dto == null ? null : dto.getItems();
        if (items == null || items.isEmpty()) return out;

        List<Long> loanIds = items.stream().map(LoanRepaySyncDTO.Item::getLoanId).distinct().collect(Collectors.toList());
        Map<Long, FamilyLoan> loans = familyLoanMapper.selectBatchIds(loanIds).stream()
                .filter(l -> Objects.equals(l.getFamilyId(), familyId))
                .collect(Collectors.toMap(FamilyLoan::getId, l -> l));
        if (loans.size() != loanIds.size()) throw new BizException(ResultCode.NOT_FOUND);

        // 账户归属:一次批量查,非本家庭/已删除的账户一律拒绝写入
        List<Long> accountIds = items.stream().map(LoanRepaySyncDTO.Item::getAccountId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, BookAccount> accounts = accountIds.isEmpty() ? Map.of()
                : bookAccountMapper.selectBatchIds(accountIds).stream()
                        .filter(a -> Objects.equals(a.getFamilyId(), familyId))
                        .collect(Collectors.toMap(BookAccount::getId, a -> a));
        if (accounts.size() != accountIds.size()) {
            throw new BizException(ResultCode.BAD_REQUEST, "所选还款账户已失效，请重新选择");
        }

        // 每笔贷款配置过的账户集合(初始账户 + 换账户事件),只用于校验,不做期次归属推断
        Map<Long, Set<Long>> allowedAccounts = allowedAccountsByLoan(loans);

        Map<String, FamilyLoanRepay> booked = familyLoanRepayMapper.selectList(new LambdaQueryWrapper<FamilyLoanRepay>()
                        .eq(FamilyLoanRepay::getFamilyId, familyId)
                        .in(FamilyLoanRepay::getLoanId, loanIds))
                .stream().collect(Collectors.toMap(r -> key(r.getLoanId(), r.getPeriod()), r -> r, (a, b) -> a));

        // 已记账期次对应的记账流水一次取回(改账户/改金额都要回写它,禁止逐条 selectById)
        List<Long> bookedRecordIds = booked.values().stream().map(FamilyLoanRepay::getRecordId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, BookRecord> records = bookedRecordIds.isEmpty() ? Map.of()
                : bookRecordMapper.selectBatchIds(bookedRecordIds).stream()
                        .filter(r -> Objects.equals(r.getFamilyId(), familyId))
                        .collect(Collectors.toMap(BookRecord::getId, r -> r, (a, b) -> a));

        LocalDate today = LocalDate.now();
        int created = 0;
        // 已记账期次:改挂新账户的笔数(含换账户)、按最新计划重算金额/日期的流水笔数
        int moved = 0;
        int adjusted = 0;
        for (LoanRepaySyncDTO.Item item : items) {
            if (item == null || item.getLoanId() == null || item.getAccountId() == null) continue;
            FamilyLoan loan = loans.get(item.getLoanId());
            Integer period = item.getPeriod();
            if (period == null || period < 1 || period > loan.getMonths()) {
                throw new BizException(ResultCode.BAD_REQUEST, "还款期次超出贷款期数，请核对后重试");
            }
            if (item.getDueDate() == null || item.getDueDate().isAfter(today)) {
                throw new BizException(ResultCode.BAD_REQUEST, "还款日还没到，不能提前记账");
            }
            if (item.getAmount() == null || item.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException(ResultCode.BAD_REQUEST, "还款额无效，请核对贷款信息");
            }
            Set<Long> allowed = allowedAccounts.getOrDefault(loan.getId(), Set.of());
            if (!allowed.contains(item.getAccountId())) {
                throw new BizException(ResultCode.BAD_REQUEST, "所选账户不是这笔贷款的还款账户");
            }
            FamilyLoanRepay existing = booked.get(key(loan.getId(), period));
            if (existing != null) {
                boolean accountChanged = !Objects.equals(existing.getAccountId(), item.getAccountId());
                // 贷款信息被改过(利率/期限/首期还款日/事件):已记账的金额、还款日也要跟着最新流水走
                boolean planChanged = !sameAmount(existing.getAmount(), item.getAmount())
                        || !sameAmount(existing.getPrincipal(), item.getPrincipal())
                        || !sameAmount(existing.getInterest(), item.getInterest())
                        || !Objects.equals(existing.getDueDate(), item.getDueDate());
                if (accountChanged || planChanged) {
                    familyLoanRepayMapper.update(null, new LambdaUpdateWrapper<FamilyLoanRepay>()
                            .eq(FamilyLoanRepay::getId, existing.getId())
                            .set(FamilyLoanRepay::getAccountId, item.getAccountId())
                            .set(FamilyLoanRepay::getAmount, item.getAmount())
                            .set(FamilyLoanRepay::getPrincipal, item.getPrincipal())
                            .set(FamilyLoanRepay::getInterest, item.getInterest())
                            .set(FamilyLoanRepay::getDueDate, item.getDueDate()));
                }
                BookRecord record = existing.getRecordId() == null ? null : records.get(existing.getRecordId());
                if (record != null) {
                    // 未被手改 = 流水上的金额/日期/账户仍与台账快照一致;任一项被改过就整条保留用户的选择
                    boolean untouched = sameAmount(record.getAmount(), existing.getAmount())
                            && Objects.equals(record.getRecordDate(), existing.getDueDate())
                            && Objects.equals(record.getAccountId(), existing.getAccountId());
                    String remark = loan.getName() + " · 第 " + period + " 期";
                    if (untouched && (accountChanged || planChanged || !Objects.equals(record.getRemark(), remark))) {
                        LambdaUpdateWrapper<BookRecord> rw = new LambdaUpdateWrapper<BookRecord>()
                                .eq(BookRecord::getId, record.getId())
                                .set(BookRecord::getAccountId, item.getAccountId())
                                .set(BookRecord::getRemark, remark);
                        if (planChanged) {
                            rw.set(BookRecord::getAmount, item.getAmount())
                                    .set(BookRecord::getRecordDate, item.getDueDate());
                        }
                        bookRecordMapper.update(null, rw);
                        if (planChanged) adjusted++;
                    }
                }
                if (accountChanged) moved++;
                // 台账跟着最新计划走,作为下一轮比较的基准
                existing.setAccountId(item.getAccountId());
                existing.setAmount(item.getAmount());
                existing.setPrincipal(item.getPrincipal());
                existing.setInterest(item.getInterest());
                existing.setDueDate(item.getDueDate());
                continue;
            }

            // 先占住 (贷款, 期次) 唯一键再写记账流水:两个标签页/手机同时同步时,
            // 抢输的一方在这里被唯一键挡下(此时流水还没写,不会留下重复支出)
            FamilyLoanRepay repay = new FamilyLoanRepay();
            repay.setFamilyId(familyId);
            repay.setLoanId(loan.getId());
            repay.setPeriod(period);
            repay.setDueDate(item.getDueDate());
            repay.setAmount(item.getAmount());
            repay.setPrincipal(item.getPrincipal());
            repay.setInterest(item.getInterest());
            repay.setAccountId(item.getAccountId());
            try {
                familyLoanRepayMapper.insert(repay);
            } catch (DuplicateKeyException e) {
                log.debug("该期已被并发同步记账,跳过,loanId={},period={}", loan.getId(), period);
                continue;
            }
            booked.put(key(loan.getId(), period), repay); // 同批重复项不再插入第二次

            BookRecord record = new BookRecord();
            record.setFamilyId(familyId);
            record.setType(DictConst.BOOK_EXPENSE);
            record.setAmount(item.getAmount());
            record.setCategory(REPAY_CATEGORY);
            record.setRemark(loan.getName() + " · 第 " + period + " 期");
            record.setRecordDate(item.getDueDate());
            // 记账人记成贷款登记人:自动流水不属于触发同步的人,谁先打开页面不该影响「记录人」
            record.setCreatedBy(loan.getCreatedBy());
            record.setAccountId(item.getAccountId());
            bookRecordMapper.insert(record);

            if (record.getId() != null) {
                familyLoanRepayMapper.update(null, new LambdaUpdateWrapper<FamilyLoanRepay>()
                        .eq(FamilyLoanRepay::getId, repay.getId())
                        .set(FamilyLoanRepay::getRecordId, record.getId()));
            }
            created++;
        }

        out.put("created", created);
        out.put("moved", moved);
        out.put("adjusted", adjusted);
        return out;
    }

    private FamilyLoan require(Long id, Long familyId) {
        FamilyLoan loan = familyLoanMapper.selectById(id);
        if (loan == null || !loan.getFamilyId().equals(familyId)) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        return loan;
    }

    /** 表单校验与归一(枚举一律大写英文单词;金额/期数/利率越界直接报错) */
    private void applyForm(FamilyLoan loan, FamilyLoanDTO dto) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "请填写贷款名称");
        }
        if (dto.getAmount() == null || dto.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "请填写大于 0 的贷款本金");
        }
        Integer months = dto.getMonths();
        if (months == null || months < 1 || months > MAX_MONTHS) {
            throw new BizException(ResultCode.BAD_REQUEST, "请填写 1~1200 期内的还款期数");
        }
        BigDecimal rate = dto.getRate();
        if (!validRate(rate)) {
            throw new BizException(ResultCode.BAD_REQUEST, "请填写 0~99.9999 之间的年利率(最多四位小数)");
        }
        LocalDate loanDate = dto.getLoanDate();
        LocalDate firstPayDate = dto.getFirstPayDate();
        if (loanDate != null && firstPayDate != null && firstPayDate.isBefore(loanDate)) {
            throw new BizException(ResultCode.BAD_REQUEST, "首期还款日不能早于放款日");
        }
        BigDecimal firstPayment = dto.getFirstPayment();
        if (firstPayment != null && firstPayment.compareTo(BigDecimal.ZERO) <= 0) {
            firstPayment = null;
        }

        loan.setName(dto.getName().trim());
        loan.setChannel(normalize(dto.getChannel(), CHANNELS, "OTHER"));
        loan.setMethod(normalize(dto.getMethod(), METHODS, "EQUAL_INSTALLMENT"));
        loan.setAmount(dto.getAmount());
        loan.setMonths(months);
        loan.setLoanDate(loanDate);
        loan.setFirstPayDate(firstPayDate);
        loan.setFirstPayment(firstPayment);
        loan.setRate(rate);
        loan.setAccountId(dto.getAccountId());
        loan.setGroupName(blankToNull(dto.getGroupName()));
        loan.setNote(blankToNull(dto.getNote()));
    }

    /**
     * 没有初始还款账户时,「第 1 期还款之后换账户」实际就是首次绑定:第 1 期还款前根本还没有账户,
     * 不存在「第 1 期后」这个变更点。用户按「绑定账户的日期」填事件时最常落到第 1 期(日期早于
     * 首期还款日会被推成第 1 期),若原样保存,第 1 期会因为「该期无账户」永远不自动记账。
     * 因此把这**一条**事件并成初始账户(第 1 期起生效)并去掉它;已有初始账户时不动 —— 那才是
     * 真正的「第 1 期还款之后换账户」(第 2 期起改扣新账户)。
     */
    private void foldInitialAccountChange(Long familyId, FamilyLoan loan, FamilyLoanDTO dto) {
        if (loan.getAccountId() != null || dto.getEvents() == null || dto.getEvents().isEmpty()) return;
        List<FamilyLoanDTO.EventDTO> kept = new ArrayList<>();
        boolean folded = false;
        for (FamilyLoanDTO.EventDTO e : dto.getEvents()) {
            if (e == null) continue;
            boolean accountChange = e.getType() != null && "ACCOUNT_CHANGE".equalsIgnoreCase(e.getType().trim());
            if (!folded && accountChange && e.getAccountId() != null
                    && e.getEffectivePeriod() != null && e.getEffectivePeriod() == 1) {
                requireAccounts(familyId, List.of(e.getAccountId()));
                loan.setAccountId(e.getAccountId());
                folded = true;
                continue;
            }
            kept.add(e);
        }
        if (folded) dto.setEvents(kept);
    }

    /** 事件时间轴整体写入(调用前已物理清空) */
    private void insertEvents(Long loanId, Long familyId, FamilyLoanDTO dto) {
        List<FamilyLoanDTO.EventDTO> events = dto.getEvents();
        if (events == null || events.isEmpty()) return;
        List<FamilyLoanEvent> rows = new ArrayList<>();
        for (FamilyLoanDTO.EventDTO e : events) {
            if (e == null) continue;
            Integer period = e.getEffectivePeriod();
            if (period == null || period < 1 || period > dto.getMonths()) {
                throw new BizException(ResultCode.BAD_REQUEST, "事件生效期次需在还款期数内");
            }
            String type = e.getType() == null ? "" : e.getType().trim().toUpperCase();
            if (!EVENT_TYPES.contains(type)) {
                throw new BizException(ResultCode.BAD_REQUEST, "事件类型不正确");
            }
            FamilyLoanEvent row = new FamilyLoanEvent();
            row.setLoanId(loanId);
            row.setEffectivePeriod(period);
            row.setType(type);
            if ("RATE_CHANGE".equals(type)) {
                BigDecimal rate = e.getRate();
                if (!validRate(rate)) {
                    throw new BizException(ResultCode.BAD_REQUEST, "调整后的年利率需在 0~99.9999 之间(最多四位小数)");
                }
                row.setRate(rate);
            } else if ("PREPAY".equals(type)) {
                BigDecimal amount = e.getAmount();
                if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BizException(ResultCode.BAD_REQUEST, "提前还款金额需大于 0");
                }
                row.setAmount(amount);
                row.setStrategy(normalize(e.getStrategy(), PREPAY_STRATEGIES, "SHORTEN"));
            } else {
                // ACCOUNT_CHANGE:account_id 为空表示从该期起不再自动记账(解绑)
                row.setAccountId(e.getAccountId());
            }
            row.setNote(blankToNull(e.getNote()));
            row.setTriggerDate(e.getTriggerDate());
            rows.add(row);
        }
        requireAccounts(familyId, rows.stream().map(FamilyLoanEvent::getAccountId).collect(Collectors.toList()));
        for (FamilyLoanEvent row : rows) {
            familyLoanEventMapper.insert(row);
        }
    }

    /** 账户必须属于本家庭且未删除;空值(未绑定/解绑)直接放行 */
    private void requireAccounts(Long familyId, List<Long> accountIds) {
        List<Long> ids = accountIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) return;
        Map<Long, BookAccount> found = bookAccountMapper.selectBatchIds(ids).stream()
                .filter(a -> Objects.equals(a.getFamilyId(), familyId))
                .collect(Collectors.toMap(BookAccount::getId, a -> a));
        if (found.size() != ids.size()) {
            throw new BizException(ResultCode.BAD_REQUEST, "所选还款账户已失效，请重新选择");
        }
    }

    /** 每笔贷款配置过的账户集合(初始账户 + 换账户事件),用于同步时校验写入目标 */
    private Map<Long, Set<Long>> allowedAccountsByLoan(Map<Long, FamilyLoan> loans) {
        Map<Long, Set<Long>> out = new HashMap<>();
        for (FamilyLoan loan : loans.values()) {
            Set<Long> set = new HashSet<>();
            if (loan.getAccountId() != null) set.add(loan.getAccountId());
            out.put(loan.getId(), set);
        }
        for (FamilyLoanEvent e : familyLoanEventMapper.selectList(new LambdaQueryWrapper<FamilyLoanEvent>()
                .in(FamilyLoanEvent::getLoanId, loans.keySet())
                .eq(FamilyLoanEvent::getType, "ACCOUNT_CHANGE"))) {
            if (e.getAccountId() != null) out.computeIfAbsent(e.getLoanId(), k -> new HashSet<>()).add(e.getAccountId());
        }
        return out;
    }

    /** 已记账期次按贷款归组(列表页一次取回,前端据此只提交缺的期次) */
    private Map<Long, List<Integer>> bookedPeriods(Long familyId) {
        return familyLoanRepayMapper.selectBookedPeriods(familyId).stream()
                .collect(Collectors.groupingBy(FamilyLoanRepay::getLoanId,
                        Collectors.mapping(FamilyLoanRepay::getPeriod, Collectors.toList())));
    }

    private Map<String, Object> toEventMap(FamilyLoanEvent e, Map<Long, String> accountNames) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", e.getId());
        map.put("type", e.getType());
        map.put("effectivePeriod", e.getEffectivePeriod());
        map.put("rate", e.getRate());
        map.put("amount", e.getAmount());
        map.put("strategy", e.getStrategy());
        map.put("accountId", e.getAccountId());
        map.put("accountName", accountName(e.getAccountId(), accountNames));
        map.put("triggerDate", e.getTriggerDate());
        map.put("note", e.getNote());
        map.put("createdAt", e.getCreatedAt());
        return map;
    }

    /** 账户名批量回填:id → 名称(已删除账户查不到,回填时提示「已删除账户」) */
    private Map<Long, String> accountNames(List<Long> ids) {
        List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (distinct.isEmpty()) return Map.of();
        return bookAccountMapper.selectBatchIds(distinct).stream()
                .collect(Collectors.toMap(BookAccount::getId, BookAccount::getName, (a, b) -> a));
    }

    private static String accountName(Long id, Map<Long, String> names) {
        if (id == null) return null;
        return names.getOrDefault(id, "已删除账户");
    }

    private static String key(Long loanId, Integer period) {
        return loanId + ":" + period;
    }

    /** 金额按数值比较:台账快照 2481.34 与前端入参 2481.3400 视为相同 */
    private static boolean sameAmount(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) return a == null && b == null;
        return a.compareTo(b) == 0;
    }

    private String normalize(String value, Set<String> allowed, String fallback) {
        if (value == null) return fallback;
        String upper = value.trim().toUpperCase();
        return allowed.contains(upper) ? upper : fallback;
    }

    /** 年利率边界:非负、不超列上限 99.9999、最多四位小数(第五位会被四舍五入进位成 100.0000 越界) */
    private boolean validRate(BigDecimal rate) {
        return rate != null
                && rate.compareTo(BigDecimal.ZERO) >= 0
                && rate.compareTo(MAX_RATE) <= 0
                && rate.stripTrailingZeros().scale() <= 4;
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
