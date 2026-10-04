package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.FamilyLoanDTO;
import com.ihomy.entity.FamilyLoan;
import com.ihomy.entity.FamilyLoanEvent;
import com.ihomy.entity.SysUser;
import com.ihomy.mapper.FamilyLoanEventMapper;
import com.ihomy.mapper.FamilyLoanMapper;
import com.ihomy.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 家庭贷款记录业务:登记真实贷款与事件时间轴(利率调整/提前还款),还款流水由前端按事件重算
 * (utils/loan.js 的 loanLedger,口径与银行一致:利息 = 剩余本金 × 月利率逐期取整到分、末期结清)。
 *
 * 贷款为家庭共享数据,全员可见;编辑删除不限创建人(持 loan:manage 即可,照家庭账目口径)。
 * 事件在编辑时整体重写(物理删后重建),不设逻辑删。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoanRecordService {

    private final FamilyLoanMapper familyLoanMapper;
    private final FamilyLoanEventMapper familyLoanEventMapper;
    private final SysUserMapper sysUserMapper;

    private static final Set<String> CHANNELS = Set.of("COMMERCIAL", "FUND", "OTHER");
    private static final Set<String> METHODS = Set.of("EQUAL_INSTALLMENT", "EQUAL_PRINCIPAL");
    private static final Set<String> EVENT_TYPES = Set.of("RATE_CHANGE", "PREPAY");
    private static final Set<String> PREPAY_STRATEGIES = Set.of("SHORTEN", "REDUCE");

    private static final int MAX_MONTHS = 1200;
    /** 年利率上限与 rate 列 DECIMAL(6,4) 对齐:填 100 或第五位小数四舍五入进位都会落库越界走 500 */
    private static final BigDecimal MAX_RATE = new BigDecimal("99.9999");

    /** 贷款列表(含各自的事件时间轴),创建人昵称批量回填(不做 N+1) */
    public List<Map<String, Object>> list(Long familyId) {
        List<FamilyLoan> loans = familyLoanMapper.selectList(new LambdaQueryWrapper<FamilyLoan>()
                .eq(FamilyLoan::getFamilyId, familyId)
                .orderByDesc(FamilyLoan::getCreatedAt));
        if (loans.isEmpty()) return List.of();

        List<Long> loanIds = loans.stream().map(FamilyLoan::getId).collect(Collectors.toList());
        Map<Long, List<Map<String, Object>>> eventsByLoan = familyLoanEventMapper.selectList(
                        new LambdaQueryWrapper<FamilyLoanEvent>().in(FamilyLoanEvent::getLoanId, loanIds)
                                // 同期次事件按 id 定序:事件序决定前端 loanLedger 重算结果,必须全客户端一致
                                .orderByAsc(FamilyLoanEvent::getEffectivePeriod, FamilyLoanEvent::getId))
                .stream().collect(Collectors.groupingBy(FamilyLoanEvent::getLoanId,
                        Collectors.mapping(this::toEventMap, Collectors.toList())));

        Map<Long, String> names = sysUserMapper.selectBatchIds(
                        loans.stream().map(FamilyLoan::getCreatedBy).distinct().collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(SysUser::getId,
                        u -> u.getNickname() != null ? u.getNickname() : u.getUsername(), (a, b) -> a));

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
            map.put("groupName", l.getGroupName());
            map.put("note", l.getNote());
            map.put("createdBy", l.getCreatedBy());
            map.put("createdByName", names.getOrDefault(l.getCreatedBy(), "未知成员"));
            map.put("createdAt", l.getCreatedAt());
            map.put("updatedAt", l.getUpdatedAt());
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
        familyLoanMapper.insert(loan);
        insertEvents(loan.getId(), dto);
        return loan.getId();
    }

    /** 编辑贷款:基本信息与事件时间轴整体替换 */
    @Transactional
    public void update(Long id, Long familyId, FamilyLoanDTO dto) {
        FamilyLoan loan = require(id, familyId);
        applyForm(loan, dto);
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
                .set(FamilyLoan::getGroupName, loan.getGroupName())
                .set(FamilyLoan::getNote, loan.getNote());
        familyLoanMapper.update(null, uw);
        familyLoanEventMapper.deleteByLoanId(id);
        insertEvents(id, dto);
    }

    @Transactional
    public void delete(Long id, Long familyId) {
        require(id, familyId);
        familyLoanMapper.deleteById(id);
        familyLoanEventMapper.deleteByLoanId(id);
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
        loan.setGroupName(blankToNull(dto.getGroupName()));
        loan.setNote(blankToNull(dto.getNote()));
    }

    /** 事件时间轴整体写入(调用前已物理清空) */
    private void insertEvents(Long loanId, FamilyLoanDTO dto) {
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
            } else {
                BigDecimal amount = e.getAmount();
                if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BizException(ResultCode.BAD_REQUEST, "提前还款金额需大于 0");
                }
                row.setAmount(amount);
                row.setStrategy(normalize(e.getStrategy(), PREPAY_STRATEGIES, "SHORTEN"));
            }
            row.setNote(blankToNull(e.getNote()));
            row.setTriggerDate(e.getTriggerDate());
            rows.add(row);
        }
        for (FamilyLoanEvent row : rows) {
            familyLoanEventMapper.insert(row);
        }
    }

    private Map<String, Object> toEventMap(FamilyLoanEvent e) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", e.getId());
        map.put("type", e.getType());
        map.put("effectivePeriod", e.getEffectivePeriod());
        map.put("rate", e.getRate());
        map.put("amount", e.getAmount());
        map.put("strategy", e.getStrategy());
        map.put("triggerDate", e.getTriggerDate());
        map.put("note", e.getNote());
        map.put("createdAt", e.getCreatedAt());
        return map;
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
