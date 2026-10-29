package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.DictConst;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.FinanceDTO;
import com.ihomy.entity.BookAccount;
import com.ihomy.entity.BookRecord;
import com.ihomy.entity.FamilyFinanceProduct;
import com.ihomy.entity.SysUser;
import com.ihomy.mapper.BookAccountMapper;
import com.ihomy.mapper.BookRecordMapper;
import com.ihomy.mapper.FamilyFinanceProductMapper;
import com.ihomy.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 理财储蓄业务:登记家庭持仓(存款/理财/基金等),并把「购买转出 / 赎回转入 / 收益收入」
 * 按用户勾选记进记账本(同一事务,失败整体回滚)。
 *
 * 口径:本金不属于收支(现金流与资产分开),只有收益进收支;预期利息与进度由前端
 * utils/finance.js 按同一公式实时算,不落库。到期与否以到期日派生,库内 status 不自动改。
 * 已入账防重复:invest/redeem/interest_record_id 命中即拒绝再次记账。
 */
@Service
@RequiredArgsConstructor
public class FinanceService {

    private final FamilyFinanceProductMapper productMapper;
    private final BookRecordMapper bookMapper;
    private final BookAccountMapper accountMapper;
    private final SysUserMapper sysUserMapper;

    /** 年化上限与 annual_rate 列 DECIMAL(6,4) 对齐:100 或第五位小数四舍五入进位都会落库越界走 500 */
    private static final BigDecimal MAX_RATE = new BigDecimal("99.9999");
    private static final int MAX_TERM_MONTHS = 1200;
    /** 收益入账用的分类名(总账按分类统计时单列一项) */
    private static final String INTEREST_CATEGORY = "理财收益";

    /** 持仓列表:创建人昵称与账户名各批量查一次(禁 N+1) */
    public List<Map<String, Object>> list(Long familyId) {
        List<FamilyFinanceProduct> products = productMapper.selectList(new LambdaQueryWrapper<FamilyFinanceProduct>()
                .eq(FamilyFinanceProduct::getFamilyId, familyId)
                .orderByDesc(FamilyFinanceProduct::getCreatedAt)
                .orderByDesc(FamilyFinanceProduct::getId));
        if (products.isEmpty()) return List.of();

        Map<Long, String> names = sysUserMapper.selectBatchIds(products.stream()
                        .map(FamilyFinanceProduct::getCreatedBy).distinct().collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(SysUser::getId,
                        u -> u.getNickname() != null && !u.getNickname().isBlank() ? u.getNickname() : u.getUsername(),
                        (a, b) -> a));

        List<Long> accountIds = products.stream().map(FamilyFinanceProduct::getAccountId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, String> accountNames = accountIds.isEmpty() ? Map.of()
                : accountMapper.selectBatchIds(accountIds).stream()
                .collect(Collectors.toMap(BookAccount::getId, BookAccount::getName, (a, b) -> a));

        return products.stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getName());
            m.put("productType", p.getProductType());
            m.put("institution", p.getInstitution());
            m.put("accountId", p.getAccountId());
            m.put("accountName", p.getAccountId() == null ? null : accountNames.getOrDefault(p.getAccountId(), "已删除账户"));
            m.put("principal", p.getPrincipal());
            m.put("annualRate", p.getAnnualRate());
            m.put("interestMode", p.getInterestMode());
            m.put("dayCount", p.getDayCount());
            m.put("termMonths", p.getTermMonths());
            m.put("startDate", p.getStartDate());
            m.put("endDate", p.getEndDate());
            m.put("status", p.getStatus());
            m.put("redeemDate", p.getRedeemDate());
            m.put("redeemAmount", p.getRedeemAmount());
            m.put("actualInterest", p.getActualInterest());
            m.put("interestBooked", p.getInterestBooked() != null && p.getInterestBooked() == 1);
            m.put("investBooked", p.getInvestRecordId() != null);
            m.put("redeemBooked", p.getRedeemRecordId() != null);
            m.put("investRecordId", p.getInvestRecordId());
            m.put("redeemRecordId", p.getRedeemRecordId());
            m.put("interestRecordId", p.getInterestRecordId());
            m.put("riskLevel", p.getRiskLevel());
            m.put("note", p.getNote());
            m.put("createdBy", p.getCreatedBy());
            m.put("createdByName", names.getOrDefault(p.getCreatedBy(), "未知成员"));
            m.put("createdAt", p.getCreatedAt());
            return m;
        }).collect(Collectors.toList());
    }

    /** 新建持仓:校验后落库;勾选购买转出时同事务记一笔转账并回写明细 id */
    @Transactional
    public FamilyFinanceProduct create(Long userId, Long familyId, FinanceDTO dto) {
        FamilyFinanceProduct p = new FamilyFinanceProduct();
        p.setFamilyId(familyId);
        p.setCreatedBy(userId);
        applyForm(p, familyId, dto);
        if (p.getPrincipal() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "请填写本金");
        }
        if (p.getStatus() == null) p.setStatus(DictConst.FINANCE_HOLDING);
        validate(p);
        productMapper.insert(p);

        if (Boolean.TRUE.equals(dto.getBookInvestTransfer())) {
            p.setInvestRecordId(insertInvestRecord(p, userId, familyId));
            persist(p);
        }
        return p;
    }

    /** 编辑持仓:仅创建人本人或家长可改;按需追加赎回/收益入账,已记过的拒绝重复 */
    @Transactional
    public void update(Long id, Long familyId, Long userId, boolean isOwner, FinanceDTO dto) {
        FamilyFinanceProduct p = require(id, familyId);
        if (!p.getCreatedBy().equals(userId) && !isOwner) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        applyForm(p, familyId, dto);
        validate(p);

        boolean wantInvest = Boolean.TRUE.equals(dto.getBookInvestTransfer());
        boolean wantRedeem = Boolean.TRUE.equals(dto.getBookRedeemTransfer());
        boolean wantInterest = Boolean.TRUE.equals(dto.getBookInterest());
        if (wantInvest && p.getInvestRecordId() != null) {
            throw new BizException(ResultCode.BAD_REQUEST, "该笔购买已经记过账，不能重复记账");
        }
        if (wantRedeem && p.getRedeemRecordId() != null) {
            throw new BizException(ResultCode.BAD_REQUEST, "该笔赎回已经记过账，不能重复记账");
        }
        if (wantInterest && p.getInterestBooked() != null && p.getInterestBooked() == 1) {
            throw new BizException(ResultCode.BAD_REQUEST, "该笔收益已经记过账，不能重复记账");
        }

        if (wantInvest || wantRedeem || wantInterest) {
            requireAccount(p, "记账前请先选择资金账户");
        }
        // 赎回日缺省按今天:前端赎回弹窗总会带,手工改单时兜底
        if ((wantRedeem || wantInterest) && p.getRedeemDate() == null) {
            p.setRedeemDate(LocalDate.now());
        }
        if (wantInterest && p.getActualInterest() == null) {
            if (p.getRedeemAmount() == null) {
                throw new BizException(ResultCode.BAD_REQUEST, "请填写到账金额或实际收益");
            }
            p.setActualInterest(p.getRedeemAmount().subtract(p.getPrincipal()));
        }

        if (wantInvest) p.setInvestRecordId(insertInvestRecord(p, userId, familyId));
        if (wantRedeem) p.setRedeemRecordId(insertRedeemRecord(p, userId, familyId));
        if (wantInterest) {
            p.setInterestRecordId(insertInterestRecord(p, userId, familyId));
            p.setInterestBooked(1);
        }
        persist(p);
    }

    /** 删除持仓:仅创建人本人或家长;逻辑删(已记过账的明细保留,不做连带回滚) */
    public void delete(Long id, Long familyId, Long userId, boolean isOwner) {
        FamilyFinanceProduct p = require(id, familyId);
        if (!p.getCreatedBy().equals(userId) && !isOwner) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        productMapper.deleteById(id);
    }

    // ------------------------------------------------------------------ 表单合并与校验

    /**
     * 把表单合并进实体:null = 不修改(沿用库内值),文本空串 = 清空,accountId 传 0 = 不挂账户。
     * 枚举一律归一成字典词,到期日按「显式填了用填的,改了期限用起息日 + 期限补算」定形。
     */
    private void applyForm(FamilyFinanceProduct p, Long familyId, FinanceDTO dto) {
        if (dto.getName() != null) p.setName(dto.getName().trim());
        if (dto.getProductType() != null) p.setProductType(DictConst.financeType(dto.getProductType()));
        if (p.getProductType() == null) p.setProductType(DictConst.FINANCE_TYPE_DEPOSIT);
        if (dto.getInstitution() != null) p.setInstitution(blankToNull(dto.getInstitution()));
        if (dto.getAccountId() != null) {
            p.setAccountId(dto.getAccountId() <= 0 ? null : dto.getAccountId());
        }
        if (dto.getPrincipal() != null) p.setPrincipal(dto.getPrincipal());
        if (dto.getAnnualRate() != null) p.setAnnualRate(dto.getAnnualRate());
        if (dto.getInterestMode() != null) p.setInterestMode(DictConst.financeMode(dto.getInterestMode()));
        if (p.getInterestMode() == null) p.setInterestMode(DictConst.FINANCE_MODE_BY_MONTH);
        if (dto.getDayCount() != null) p.setDayCount(DictConst.financeDayCount(dto.getDayCount()));
        if (p.getDayCount() == null) p.setDayCount(365);
        if (dto.getStartDate() != null) p.setStartDate(dto.getStartDate());
        if (dto.getTermMonths() != null) p.setTermMonths(dto.getTermMonths());
        if (dto.getStatus() != null) p.setStatus(DictConst.financeStatus(dto.getStatus()));
        if (dto.getRedeemDate() != null) p.setRedeemDate(dto.getRedeemDate());
        if (dto.getRedeemAmount() != null) p.setRedeemAmount(dto.getRedeemAmount());
        if (dto.getActualInterest() != null) p.setActualInterest(dto.getActualInterest());
        if (dto.getRiskLevel() != null) p.setRiskLevel(blankToNull(dto.getRiskLevel()));
        if (dto.getNote() != null) p.setNote(blankToNull(dto.getNote()));
        if (p.getFamilyId() == null) p.setFamilyId(familyId);

        // 到期日:显式给了用给的;改了期限就按起息日 + 期限重算(改期限覆盖旧到期日)
        if (dto.getEndDate() != null) {
            p.setEndDate(dto.getEndDate());
        } else if (dto.getTermMonths() != null) {
            p.setEndDate(computeEndDate(p.getStartDate(), p.getTermMonths()));
        } else if (p.getEndDate() == null) {
            p.setEndDate(computeEndDate(p.getStartDate(), p.getTermMonths()));
        }
    }

    /** 到期日补算:有期限(>0 月)时按起息日顺延,否则为活期(空) */
    static LocalDate computeEndDate(LocalDate startDate, Integer termMonths) {
        if (startDate == null || termMonths == null || termMonths <= 0) return null;
        return startDate.plusMonths(termMonths);
    }

    /** 日期与利率校验(实体已合并表单):越界给 400 中文提示,不让数据库约束炸成 500 */
    private void validate(FamilyFinanceProduct p) {
        if (p.getPrincipal() == null || p.getPrincipal().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "本金必须大于 0");
        }
        BigDecimal rate = p.getAnnualRate();
        if (rate != null) {
            if (rate.compareTo(BigDecimal.ZERO) < 0 || rate.compareTo(MAX_RATE) > 0) {
                throw new BizException(ResultCode.BAD_REQUEST, "年化利率需在 0~99.9999% 之间");
            }
            if (rate.stripTrailingZeros().scale() > 4) {
                throw new BizException(ResultCode.BAD_REQUEST, "年化利率最多 4 位小数");
            }
        }
        if (p.getTermMonths() != null && (p.getTermMonths() < 0 || p.getTermMonths() > MAX_TERM_MONTHS)) {
            throw new BizException(ResultCode.BAD_REQUEST, "期限需在 0~" + MAX_TERM_MONTHS + " 个月之间");
        }
        if (p.getStartDate() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "请选择起息日");
        }
        if (p.getEndDate() != null && p.getEndDate().isBefore(p.getStartDate())) {
            throw new BizException(ResultCode.BAD_REQUEST, "到期日不能早于起息日");
        }
        if (p.getRedeemDate() != null && p.getRedeemDate().isBefore(p.getStartDate())) {
            throw new BizException(ResultCode.BAD_REQUEST, "赎回日不能早于起息日");
        }
        if (p.getAccountId() != null) {
            BookAccount account = accountMapper.selectById(p.getAccountId());
            if (account == null || !Objects.equals(account.getFamilyId(), p.getFamilyId())) {
                throw new BizException(ResultCode.BAD_REQUEST, "所选账户已失效，请重新选择");
            }
        }
    }

    // ------------------------------------------------------------------ 记账联动

    private Long insertInvestRecord(FamilyFinanceProduct p, Long userId, Long familyId) {
        requireAccount(p, "请选择资金转出的账户");
        BookRecord r = new BookRecord();
        r.setFamilyId(familyId);
        r.setType(DictConst.BOOK_TRANSFER);
        r.setAmount(p.getPrincipal());
        r.setCategory("理财");
        r.setRemark(remarkOf(p, "买入"));
        r.setRecordDate(p.getStartDate());
        r.setFromAccountId(p.getAccountId());
        r.setCreatedBy(userId);
        bookMapper.insert(r);
        return r.getId();
    }

    /** 赎回转回本金:金额取本金,收益另记一笔收入,两者合计才是到账金额 */
    private Long insertRedeemRecord(FamilyFinanceProduct p, Long userId, Long familyId) {
        requireAccount(p, "请选择回款的账户");
        BookRecord r = new BookRecord();
        r.setFamilyId(familyId);
        r.setType(DictConst.BOOK_TRANSFER);
        r.setAmount(p.getPrincipal());
        r.setCategory("理财");
        r.setRemark(remarkOf(p, "赎回本金"));
        r.setRecordDate(p.getRedeemDate() == null ? LocalDate.now() : p.getRedeemDate());
        r.setToAccountId(p.getAccountId());
        r.setCreatedBy(userId);
        bookMapper.insert(r);
        return r.getId();
    }

    /** 收益入账:正数记收入、负数记支出(亏损),0 不落明细只打标记,避免产生 0 元账目 */
    private Long insertInterestRecord(FamilyFinanceProduct p, Long userId, Long familyId) {
        requireAccount(p, "请选择收益入账的账户");
        BigDecimal interest = p.getActualInterest();
        if (interest == null || interest.compareTo(BigDecimal.ZERO) == 0) return null;
        BookRecord r = new BookRecord();
        r.setFamilyId(familyId);
        if (interest.compareTo(BigDecimal.ZERO) > 0) {
            r.setType(DictConst.BOOK_INCOME);
            r.setAmount(interest);
        } else {
            r.setType(DictConst.BOOK_EXPENSE);
            r.setAmount(interest.negate());
        }
        r.setCategory(INTEREST_CATEGORY);
        r.setRemark(remarkOf(p, "收益"));
        r.setRecordDate(p.getRedeemDate() == null ? LocalDate.now() : p.getRedeemDate());
        r.setAccountId(p.getAccountId());
        r.setCreatedBy(userId);
        bookMapper.insert(r);
        return r.getId();
    }

    /** 记账明细备注:产品名 + 动作,便于在账本里一眼看出这笔钱的来处 */
    private static String remarkOf(FamilyFinanceProduct p, String action) {
        return p.getName() + "（理财" + action + "）";
    }

    private static void requireAccount(FamilyFinanceProduct p, String message) {
        if (p.getAccountId() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, message);
        }
    }

    /** 只 SET 业务字段,避免 updateById 回写旧 updated_at 抑制 ON UPDATE CURRENT_TIMESTAMP */
    private void persist(FamilyFinanceProduct p) {
        productMapper.update(null, new LambdaUpdateWrapper<FamilyFinanceProduct>()
                .eq(FamilyFinanceProduct::getId, p.getId())
                .set(FamilyFinanceProduct::getName, p.getName())
                .set(FamilyFinanceProduct::getProductType, p.getProductType())
                .set(FamilyFinanceProduct::getInstitution, p.getInstitution())
                .set(FamilyFinanceProduct::getAccountId, p.getAccountId())
                .set(FamilyFinanceProduct::getPrincipal, p.getPrincipal())
                .set(FamilyFinanceProduct::getAnnualRate, p.getAnnualRate())
                .set(FamilyFinanceProduct::getInterestMode, p.getInterestMode())
                .set(FamilyFinanceProduct::getDayCount, p.getDayCount())
                .set(FamilyFinanceProduct::getTermMonths, p.getTermMonths())
                .set(FamilyFinanceProduct::getStartDate, p.getStartDate())
                .set(FamilyFinanceProduct::getEndDate, p.getEndDate())
                .set(FamilyFinanceProduct::getStatus, p.getStatus())
                .set(FamilyFinanceProduct::getRedeemDate, p.getRedeemDate())
                .set(FamilyFinanceProduct::getRedeemAmount, p.getRedeemAmount())
                .set(FamilyFinanceProduct::getActualInterest, p.getActualInterest())
                .set(FamilyFinanceProduct::getInterestBooked, p.getInterestBooked() == null ? 0 : p.getInterestBooked())
                .set(FamilyFinanceProduct::getInvestRecordId, p.getInvestRecordId())
                .set(FamilyFinanceProduct::getRedeemRecordId, p.getRedeemRecordId())
                .set(FamilyFinanceProduct::getInterestRecordId, p.getInterestRecordId())
                .set(FamilyFinanceProduct::getRiskLevel, p.getRiskLevel())
                .set(FamilyFinanceProduct::getNote, p.getNote()));
    }

    private FamilyFinanceProduct require(Long id, Long familyId) {
        FamilyFinanceProduct p = productMapper.selectById(id);
        if (p == null || !p.getFamilyId().equals(familyId)) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        return p;
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
