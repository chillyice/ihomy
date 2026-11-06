package com.ihomy.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.FamilyLoanDTO;
import com.ihomy.dto.LoanRepaySyncDTO;
import com.ihomy.entity.BookAccount;
import com.ihomy.entity.BookRecord;
import com.ihomy.entity.FamilyLoan;
import com.ihomy.entity.FamilyLoanEvent;
import com.ihomy.entity.FamilyLoanRepay;
import com.ihomy.mapper.BookAccountMapper;
import com.ihomy.mapper.BookRecordMapper;
import com.ihomy.mapper.FamilyLoanEventMapper;
import com.ihomy.mapper.FamilyLoanMapper;
import com.ihomy.mapper.FamilyLoanRepayMapper;
import com.ihomy.mapper.SysUserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 年利率边界:与 rate 列 DECIMAL(6,4) 对齐,越界输入抛 400 而不是落库炸约束走 500。
 * 还款记账同步:期次范围、还款日不晚于今天、账户必须是这笔贷款配置过的账户。
 */
class LoanRecordServiceTest {

    private final FamilyLoanMapper loanMapper = mock(FamilyLoanMapper.class);
    private final FamilyLoanEventMapper eventMapper = mock(FamilyLoanEventMapper.class);
    private final FamilyLoanRepayMapper repayMapper = mock(FamilyLoanRepayMapper.class);
    private final BookAccountMapper accountMapper = mock(BookAccountMapper.class);
    private final BookRecordMapper recordMapper = mock(BookRecordMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final LoanRecordService service =
            new LoanRecordService(loanMapper, eventMapper, repayMapper, accountMapper, recordMapper, userMapper);

    /** 单测没有 MyBatis-Plus 启动扫描,手动注册实体信息,否则 LambdaWrapper 的列名解析会报错 */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), FamilyLoanRepay.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), BookRecord.class);
    }

    private FamilyLoanDTO dto(String rate, FamilyLoanDTO.EventDTO event) {
        FamilyLoanDTO d = new FamilyLoanDTO();
        d.setName("商贷");
        d.setAmount(new BigDecimal("100000"));
        d.setMonths(120);
        d.setRate(new BigDecimal(rate));
        if (event != null) d.setEvents(List.of(event));
        return d;
    }

    private FamilyLoanDTO.EventDTO rateChange(String rate) {
        FamilyLoanDTO.EventDTO e = new FamilyLoanDTO.EventDTO();
        e.setType("RATE_CHANGE");
        e.setEffectivePeriod(12);
        e.setRate(new BigDecimal(rate));
        return e;
    }

    @Test
    void rateAtColumnCeilingAccepted() {
        assertThatCode(() -> service.create(1L, 1L, dto("99.9999", null))).doesNotThrowAnyException();
    }

    @Test
    void rateAt100RejectedAsBadRequest() {
        assertThatThrownBy(() -> service.create(1L, 1L, dto("100", null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("99.9999")
                .satisfies(e -> assertThat(((BizException) e).getCode())
                        .isEqualTo(ResultCode.BAD_REQUEST.getCode()));
    }

    @Test
    void scaleBeyondFourRejectedEvenWhenValueFits() {
        assertThatThrownBy(() -> service.create(1L, 1L, dto("50.12345", null)))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.create(1L, 1L, dto("-1", null)))
                .isInstanceOf(BizException.class);
    }

    @Test
    void eventRateAt100Rejected() {
        assertThatThrownBy(() -> service.create(1L, 1L, dto("4.9", rateChange("100"))))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("99.9999");
    }

    /* ==================== 还款记账同步 ==================== */

    private FamilyLoan loanOf(long id, int months) {
        FamilyLoan loan = new FamilyLoan();
        loan.setId(id);
        loan.setFamilyId(1L);
        loan.setCreatedBy(9L);
        loan.setName("商贷");
        loan.setMonths(months);
        return loan;
    }

    private LoanRepaySyncDTO syncDto(Long accountId, LocalDate dueDate, String amount) {
        LoanRepaySyncDTO.Item item = new LoanRepaySyncDTO.Item();
        item.setLoanId(1L);
        item.setPeriod(1);
        item.setDueDate(dueDate);
        item.setAmount(new BigDecimal(amount));
        item.setAccountId(accountId);
        LoanRepaySyncDTO d = new LoanRepaySyncDTO();
        d.setItems(List.of(item));
        return d;
    }

    /** 打桩:一笔 120 期贷款、账户 7 属于本家庭、该贷款还没记过任何一期 */
    private void stubSyncContext() {
        when(loanMapper.selectBatchIds(any())).thenReturn(List.of(loanOf(1L, 120)));
        when(accountMapper.selectBatchIds(any())).thenReturn(List.of(accountOf(7L)));
        when(repayMapper.selectList(any())).thenReturn(List.of());
        when(eventMapper.selectList(any())).thenReturn(List.of());
    }

    private BookAccount accountOf(long id) {
        BookAccount account = new BookAccount();
        account.setId(id);
        account.setFamilyId(1L);
        return account;
    }

    @Test
    void syncCreatesOneRecordForDuePeriod() {
        stubSyncContext();
        // 贷款初始账户 7 → 允许写入
        when(loanMapper.selectBatchIds(any())).thenReturn(List.of(withAccount(loanOf(1L, 120), 7L)));
        Map<String, Object> res = service.syncRepays(1L, syncDto(7L, LocalDate.now().minusDays(1), "5307.26"));
        assertThat(res.get("created")).isEqualTo(1);
        assertThat(res.get("moved")).isEqualTo(0);
    }

    @Test
    void syncRejectsFutureDueDate() {
        stubSyncContext();
        when(loanMapper.selectBatchIds(any())).thenReturn(List.of(withAccount(loanOf(1L, 120), 7L)));
        assertThatThrownBy(() -> service.syncRepays(1L, syncDto(7L, LocalDate.now().plusDays(1), "5307.26")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("还款日还没到");
    }

    @Test
    void syncRejectsAccountNotConfiguredOnLoan() {
        stubSyncContext();
        // 账户 8 属于本家庭,但不是这笔贷款绑定过/换过的账户
        when(accountMapper.selectBatchIds(any())).thenReturn(List.of(accountOf(8L)));
        when(loanMapper.selectBatchIds(any())).thenReturn(List.of(withAccount(loanOf(1L, 120), 7L)));
        assertThatThrownBy(() -> service.syncRepays(1L, syncDto(8L, LocalDate.now().minusDays(1), "5307.26")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不是这笔贷款的还款账户");
    }

    @Test
    void syncRejectsPeriodBeyondTerm() {
        stubSyncContext();
        when(loanMapper.selectBatchIds(any())).thenReturn(List.of(withAccount(loanOf(1L, 12), 7L)));
        LoanRepaySyncDTO d = syncDto(7L, LocalDate.now().minusDays(1), "5307.26");
        d.getItems().get(0).setPeriod(13);
        assertThatThrownBy(() -> service.syncRepays(1L, d))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("超出贷款期数");
    }

    /* ==================== 换账户后历史账目迁移 ==================== */

    /** 已记账的一期台账(账户 7、生成的流水 ID 100) */
    private FamilyLoanRepay bookedRepay(long period, Long accountId) {
        FamilyLoanRepay r = new FamilyLoanRepay();
        r.setId(500L + period);
        r.setFamilyId(1L);
        r.setLoanId(1L);
        r.setPeriod((int) period);
        r.setDueDate(LocalDate.now().minusMonths(1));
        r.setAmount(new BigDecimal("5307.26"));
        r.setAccountId(accountId);
        r.setRecordId(100L + period);
        return r;
    }

    private BookRecord recordOf(long id, Long accountId) {
        BookRecord r = new BookRecord();
        r.setId(id);
        r.setFamilyId(1L);
        r.setAccountId(accountId);
        return r;
    }

    /** 打桩:贷款初始账户 7,第 3 期后换到账户 9(两账户都算「配置过的账户」) */
    private void stubAccountSwitch() {
        when(loanMapper.selectBatchIds(any())).thenReturn(List.of(withAccount(loanOf(1L, 120), 7L)));
        when(accountMapper.selectBatchIds(any())).thenReturn(List.of(accountOf(9L)));
        FamilyLoanEvent change = new FamilyLoanEvent();
        change.setLoanId(1L);
        change.setType("ACCOUNT_CHANGE");
        change.setEffectivePeriod(3);
        change.setAccountId(9L);
        when(eventMapper.selectList(any())).thenReturn(List.of(change));
    }

    @Test
    void syncMovesAlreadyBookedEntriesToTheNewAccount() {
        stubAccountSwitch();
        FamilyLoanRepay booked = bookedRepay(4, 7L); // 已记的期次当初扣的账户 7
        when(repayMapper.selectList(any())).thenReturn(List.of(booked));
        when(recordMapper.selectBatchIds(any())).thenReturn(List.of(recordOf(104L, 7L)));

        Map<String, Object> res = service.syncRepays(1L, syncDtoOfPeriod(4, 9L));

        // 换账户后这一期归新账户:台账与记账流水一起迁,金额/日期不动
        assertThat(res.get("created")).isEqualTo(0);
        assertThat(res.get("moved")).isEqualTo(1);
        assertThat(booked.getAccountId()).isEqualTo(9L);
        verify(repayMapper).update(isNull(), any());
        verify(recordMapper).update(isNull(), any());
    }

    @Test
    void syncKeepsUserEditedRecordAccount() {
        stubAccountSwitch();
        FamilyLoanRepay booked = bookedRepay(4, 7L);
        when(repayMapper.selectList(any())).thenReturn(List.of(booked));
        // 用户在记账本里把这笔流水改挂到账户 12:迁移不覆盖用户的选择
        when(recordMapper.selectBatchIds(any())).thenReturn(List.of(recordOf(104L, 12L)));

        service.syncRepays(1L, syncDtoOfPeriod(4, 9L));

        verify(repayMapper).update(isNull(), any()); // 台账仍是系统凭证,照实纠正
        verify(recordMapper, never()).update(any(), any());
    }

    /** 指定期次与账户的同步表单(换账户场景要落在被迁移的那一期上) */
    private LoanRepaySyncDTO syncDtoOfPeriod(int period, Long accountId) {
        LoanRepaySyncDTO d = syncDto(accountId, LocalDate.now().minusDays(1), "5307.26");
        d.getItems().get(0).setPeriod(period);
        return d;
    }

    private FamilyLoan withAccount(FamilyLoan loan, Long accountId) {
        loan.setAccountId(accountId);
        return loan;
    }
}
