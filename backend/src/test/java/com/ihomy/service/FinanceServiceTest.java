package com.ihomy.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ihomy.common.BizException;
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
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 理财规则:年化上限与小数位、日期先后、枚举归一、到期日补算、记账联动与重复入账拒绝。
 * 全部是纯逻辑断言(不起 Spring 上下文),事务回滚由 @Transactional 注解 + 真实容器保证。
 */
class FinanceServiceTest {

    private static final Long FAMILY = 1L;
    private static final Long USER = 7L;
    private static final Long ACCOUNT = 9L;

    private final FamilyFinanceProductMapper productMapper = mock(FamilyFinanceProductMapper.class);
    private final BookRecordMapper bookMapper = mock(BookRecordMapper.class);
    private final BookAccountMapper accountMapper = mock(BookAccountMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final FinanceService service = new FinanceService(productMapper, bookMapper, accountMapper, userMapper);

    /** 单测没有 MyBatis-Plus 启动扫描,手动注册实体信息,否则 LambdaWrapper 的列名解析会报错 */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), FamilyFinanceProduct.class);
    }

    private FinanceDTO base() {
        FinanceDTO dto = new FinanceDTO();
        dto.setName("招行3年期大额存单");
        dto.setProductType("LARGE_DEPOSIT");
        dto.setPrincipal(new BigDecimal("100000"));
        dto.setAnnualRate(new BigDecimal("2.9"));
        dto.setInterestMode("BY_MONTH");
        dto.setTermMonths(36);
        dto.setStartDate(LocalDate.of(2024, 3, 12));
        return dto;
    }

    private BookAccount accountOf(Long familyId) {
        BookAccount a = new BookAccount();
        a.setId(ACCOUNT);
        a.setFamilyId(familyId);
        a.setName("招行工资卡");
        return a;
    }

    private FamilyFinanceProduct stored(Double interestBooked) {
        FamilyFinanceProduct p = new FamilyFinanceProduct();
        p.setId(1L);
        p.setFamilyId(FAMILY);
        p.setName("招行3年期大额存单");
        p.setProductType("LARGE_DEPOSIT");
        p.setAccountId(ACCOUNT);
        p.setPrincipal(new BigDecimal("100000"));
        p.setAnnualRate(new BigDecimal("2.9"));
        p.setInterestMode("BY_MONTH");
        p.setDayCount(365);
        p.setTermMonths(36);
        p.setStartDate(LocalDate.of(2024, 3, 12));
        p.setEndDate(LocalDate.of(2027, 3, 12));
        p.setStatus("HOLDING");
        p.setInterestBooked(interestBooked == null ? 0 : interestBooked.intValue());
        p.setCreatedBy(USER);
        return p;
    }

    private BookRecord captureRecord(int index) {
        ArgumentCaptor<BookRecord> captor = ArgumentCaptor.forClass(BookRecord.class);
        verify(bookMapper, times(index)).insert(captor.capture());
        return captor.getAllValues().get(index - 1);
    }

    // ---------------------------------------------------------------- 利率边界

    @Test
    void rateAtColumnCeilingAccepted() {
        FinanceDTO dto = base();
        dto.setAnnualRate(new BigDecimal("99.9999"));
        assertThatCode(() -> service.create(USER, FAMILY, dto)).doesNotThrowAnyException();
    }

    @Test
    void rateAt100Rejected() {
        FinanceDTO dto = base();
        dto.setAnnualRate(new BigDecimal("100"));
        assertThatThrownBy(() -> service.create(USER, FAMILY, dto))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("99.9999")
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(ResultCode.BAD_REQUEST.getCode()));
    }

    @Test
    void rateWithFiveDecimalsRejectedEvenWhenValueFits() {
        FinanceDTO dto = base();
        dto.setAnnualRate(new BigDecimal("2.91234"));
        assertThatThrownBy(() -> service.create(USER, FAMILY, dto))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("4 位小数");
    }

    @Test
    void zeroRateIsValidForInterestFreeProducts() {
        FinanceDTO dto = base();
        dto.setAnnualRate(BigDecimal.ZERO);
        assertThatCode(() -> service.create(USER, FAMILY, dto)).doesNotThrowAnyException();
    }

    @Test
    void floatingRateMayBeOmitted() {
        FinanceDTO dto = base();
        dto.setAnnualRate(null);
        dto.setProductType("FUND");
        assertThatCode(() -> service.create(USER, FAMILY, dto)).doesNotThrowAnyException();
        ArgumentCaptor<FamilyFinanceProduct> captor = ArgumentCaptor.forClass(FamilyFinanceProduct.class);
        verify(productMapper).insert(captor.capture());
        assertThat(captor.getValue().getAnnualRate()).isNull();
        assertThat(captor.getValue().getProductType()).isEqualTo("FUND");
    }

    // ---------------------------------------------------------------- 日期与补算

    @Test
    void principalMustBePositive() {
        FinanceDTO dto = base();
        dto.setPrincipal(BigDecimal.ZERO);
        assertThatThrownBy(() -> service.create(USER, FAMILY, dto))
                .isInstanceOf(BizException.class).hasMessageContaining("本金必须大于 0");
        assertThatThrownBy(() -> service.create(USER, FAMILY, new FinanceDTO()))
                .isInstanceOf(BizException.class).hasMessageContaining("本金");
    }

    @Test
    void startDateRequired() {
        FinanceDTO dto = base();
        dto.setStartDate(null);
        assertThatThrownBy(() -> service.create(USER, FAMILY, dto))
                .isInstanceOf(BizException.class).hasMessageContaining("起息日");
    }

    @Test
    void endDateBeforeStartDateRejected() {
        FinanceDTO dto = base();
        dto.setEndDate(LocalDate.of(2024, 3, 11));
        assertThatThrownBy(() -> service.create(USER, FAMILY, dto))
                .isInstanceOf(BizException.class).hasMessageContaining("到期日不能早于起息日");
    }

    @Test
    void redeemDateBeforeStartDateRejected() {
        FinanceDTO dto = base();
        dto.setStatus("REDEEMED");
        dto.setRedeemDate(LocalDate.of(2024, 3, 1));
        assertThatThrownBy(() -> service.create(USER, FAMILY, dto))
                .isInstanceOf(BizException.class).hasMessageContaining("赎回日不能早于起息日");
    }

    @Test
    void endDateComputedFromTermWhenAbsent() {
        FinanceDTO dto = base();
        service.create(USER, FAMILY, dto);
        ArgumentCaptor<FamilyFinanceProduct> captor = ArgumentCaptor.forClass(FamilyFinanceProduct.class);
        verify(productMapper).insert(captor.capture());
        assertThat(captor.getValue().getEndDate()).isEqualTo(LocalDate.of(2027, 3, 12));
    }

    @Test
    void openEndedProductKeepsNullEndDate() {
        FinanceDTO dto = base();
        dto.setTermMonths(0);
        service.create(USER, FAMILY, dto);
        ArgumentCaptor<FamilyFinanceProduct> captor = ArgumentCaptor.forClass(FamilyFinanceProduct.class);
        verify(productMapper).insert(captor.capture());
        assertThat(captor.getValue().getEndDate()).isNull();
    }

    @Test
    void termBeyondHundredYearsRejected() {
        FinanceDTO dto = base();
        dto.setTermMonths(1201);
        assertThatThrownBy(() -> service.create(USER, FAMILY, dto))
                .isInstanceOf(BizException.class).hasMessageContaining("期限");
    }

    // ---------------------------------------------------------------- 枚举归一

    @Test
    void enumsNormalizedCaseInsensitively() {
        FinanceDTO dto = base();
        dto.setProductType("fund");
        dto.setInterestMode("by_day");
        dto.setStatus("held");
        dto.setDayCount(360);
        service.create(USER, FAMILY, dto);
        ArgumentCaptor<FamilyFinanceProduct> captor = ArgumentCaptor.forClass(FamilyFinanceProduct.class);
        verify(productMapper).insert(captor.capture());
        FamilyFinanceProduct p = captor.getValue();
        assertThat(p.getProductType()).isEqualTo("FUND");
        assertThat(p.getInterestMode()).isEqualTo("BY_DAY");
        // 未知状态不是脏数据:归一到「持有中」,到期与否以到期日派生
        assertThat(p.getStatus()).isEqualTo("HOLDING");
        assertThat(p.getDayCount()).isEqualTo(360);
    }

    @Test
    void dayCountFallsBackTo365() {
        FinanceDTO dto = base();
        dto.setDayCount(30);
        service.create(USER, FAMILY, dto);
        ArgumentCaptor<FamilyFinanceProduct> captor = ArgumentCaptor.forClass(FamilyFinanceProduct.class);
        verify(productMapper).insert(captor.capture());
        assertThat(captor.getValue().getDayCount()).isEqualTo(365);
    }

    // ---------------------------------------------------------------- 记账联动

    @Test
    void investTransferWritesTransferAndBackfillsRecordId() {
        when(accountMapper.selectById(ACCOUNT)).thenReturn(accountOf(FAMILY));
        FinanceDTO dto = base();
        dto.setAccountId(ACCOUNT);
        dto.setBookInvestTransfer(true);
        service.create(USER, FAMILY, dto);

        BookRecord r = captureRecord(1);
        assertThat(r.getType()).isEqualTo("TRANSFER");
        assertThat(r.getAmount()).isEqualByComparingTo("100000");
        assertThat(r.getFromAccountId()).isEqualTo(ACCOUNT);
        assertThat(r.getToAccountId()).isNull();
        assertThat(r.getRecordDate()).isEqualTo(LocalDate.of(2024, 3, 12));
        assertThat(r.getFamilyId()).isEqualTo(FAMILY);
        // 回写明细 id 走 update(即使 mock 下 id 为 null 也要落一次 SET)
        verify(productMapper).update(isNull(), any());
    }

    @Test
    void investTransferWithoutAccountRejected() {
        FinanceDTO dto = base();
        dto.setBookInvestTransfer(true);
        assertThatThrownBy(() -> service.create(USER, FAMILY, dto))
                .isInstanceOf(BizException.class).hasMessageContaining("资金转出的账户");
        verify(bookMapper, never()).insert(any(BookRecord.class));
    }

    @Test
    void accountOfAnotherFamilyRejected() {
        when(accountMapper.selectById(ACCOUNT)).thenReturn(accountOf(2L));
        FinanceDTO dto = base();
        dto.setAccountId(ACCOUNT);
        assertThatThrownBy(() -> service.create(USER, FAMILY, dto))
                .isInstanceOf(BizException.class).hasMessageContaining("所选账户已失效");
    }

    @Test
    void redeemWritesTransferPlusInterestIncome() {
        when(productMapper.selectById(1L)).thenReturn(stored(0.0));
        when(accountMapper.selectById(ACCOUNT)).thenReturn(accountOf(FAMILY));
        FinanceDTO dto = new FinanceDTO();
        dto.setStatus("REDEEMED");
        dto.setRedeemDate(LocalDate.of(2027, 3, 12));
        dto.setRedeemAmount(new BigDecimal("102900"));
        dto.setActualInterest(new BigDecimal("2900"));
        dto.setBookRedeemTransfer(true);
        dto.setBookInterest(true);
        service.update(1L, FAMILY, USER, false, dto);

        ArgumentCaptor<BookRecord> captor = ArgumentCaptor.forClass(BookRecord.class);
        verify(bookMapper, times(2)).insert(captor.capture());
        List<BookRecord> rows = captor.getAllValues();
        assertThat(rows.get(0).getType()).isEqualTo("TRANSFER");
        assertThat(rows.get(0).getToAccountId()).isEqualTo(ACCOUNT);
        assertThat(rows.get(0).getAmount()).isEqualByComparingTo("100000");
        assertThat(rows.get(1).getType()).isEqualTo("INCOME");
        assertThat(rows.get(1).getAmount()).isEqualByComparingTo("2900");
        assertThat(rows.get(1).getCategory()).isEqualTo("理财收益");
        assertThat(rows.get(1).getAccountId()).isEqualTo(ACCOUNT);
        assertThat(rows.get(1).getRecordDate()).isEqualTo(LocalDate.of(2027, 3, 12));
    }

    @Test
    void negativeInterestBookedAsExpense() {
        when(productMapper.selectById(1L)).thenReturn(stored(0.0));
        when(accountMapper.selectById(ACCOUNT)).thenReturn(accountOf(FAMILY));
        FinanceDTO dto = new FinanceDTO();
        dto.setStatus("REDEEMED");
        dto.setRedeemDate(LocalDate.of(2026, 1, 12));
        dto.setRedeemAmount(new BigDecimal("98000"));
        dto.setBookInterest(true);
        service.update(1L, FAMILY, USER, false, dto);

        BookRecord r = captureRecord(1);
        assertThat(r.getType()).isEqualTo("EXPENSE");
        assertThat(r.getAmount()).isEqualByComparingTo("2000");
    }

    @Test
    void zeroInterestSkipsRecordButMarksBooked() {
        when(productMapper.selectById(1L)).thenReturn(stored(0.0));
        when(accountMapper.selectById(ACCOUNT)).thenReturn(accountOf(FAMILY));
        FinanceDTO dto = new FinanceDTO();
        dto.setStatus("REDEEMED");
        dto.setRedeemDate(LocalDate.of(2026, 1, 12));
        dto.setRedeemAmount(new BigDecimal("100000"));
        dto.setBookInterest(true);
        service.update(1L, FAMILY, USER, false, dto);
        verify(bookMapper, never()).insert(any(BookRecord.class));
        verify(productMapper).update(isNull(), any());
    }

    @Test
    void repeatedInvestBookingRejected() {
        FamilyFinanceProduct p = stored(0.0);
        p.setInvestRecordId(88L);
        when(productMapper.selectById(1L)).thenReturn(p);
        when(accountMapper.selectById(ACCOUNT)).thenReturn(accountOf(FAMILY));
        FinanceDTO dto = new FinanceDTO();
        dto.setBookInvestTransfer(true);
        assertThatThrownBy(() -> service.update(1L, FAMILY, USER, false, dto))
                .isInstanceOf(BizException.class).hasMessageContaining("不能重复记账");
        verify(bookMapper, never()).insert(any(BookRecord.class));
    }

    @Test
    void repeatedRedeemBookingRejected() {
        FamilyFinanceProduct p = stored(0.0);
        p.setRedeemRecordId(89L);
        when(productMapper.selectById(1L)).thenReturn(p);
        when(accountMapper.selectById(ACCOUNT)).thenReturn(accountOf(FAMILY));
        FinanceDTO dto = new FinanceDTO();
        dto.setBookRedeemTransfer(true);
        assertThatThrownBy(() -> service.update(1L, FAMILY, USER, false, dto))
                .isInstanceOf(BizException.class).hasMessageContaining("不能重复记账");
    }

    @Test
    void repeatedInterestBookingRejected() {
        when(productMapper.selectById(1L)).thenReturn(stored(1.0));
        when(accountMapper.selectById(ACCOUNT)).thenReturn(accountOf(FAMILY));
        FinanceDTO dto = new FinanceDTO();
        dto.setBookInterest(true);
        dto.setActualInterest(new BigDecimal("2900"));
        assertThatThrownBy(() -> service.update(1L, FAMILY, USER, false, dto))
                .isInstanceOf(BizException.class).hasMessageContaining("收益已经记过账");
    }

    @Test
    void interestDerivedFromRedeemAmountWhenAbsent() {
        when(productMapper.selectById(1L)).thenReturn(stored(0.0));
        when(accountMapper.selectById(ACCOUNT)).thenReturn(accountOf(FAMILY));
        FinanceDTO dto = new FinanceDTO();
        dto.setStatus("REDEEMED");
        dto.setRedeemDate(LocalDate.of(2027, 3, 12));
        dto.setRedeemAmount(new BigDecimal("102900"));
        dto.setBookInterest(true);
        service.update(1L, FAMILY, USER, false, dto);
        BookRecord r = captureRecord(1);
        assertThat(r.getAmount()).isEqualByComparingTo("2900");
    }

    @Test
    void failedRecordInsertPropagatesSoTransactionRollsBack() {
        when(accountMapper.selectById(ACCOUNT)).thenReturn(accountOf(FAMILY));
        when(bookMapper.insert(any(BookRecord.class))).thenThrow(new IllegalStateException("db down"));
        FinanceDTO dto = base();
        dto.setAccountId(ACCOUNT);
        dto.setBookInvestTransfer(true);
        assertThatThrownBy(() -> service.create(USER, FAMILY, dto)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void writeMethodsAreTransactional() throws NoSuchMethodException {
        Method create = FinanceService.class.getMethod("create", Long.class, Long.class, FinanceDTO.class);
        Method update = FinanceService.class.getMethod("update", Long.class, Long.class, Long.class, boolean.class, FinanceDTO.class);
        assertThat(create.isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(update.isAnnotationPresent(Transactional.class)).isTrue();
    }

    // ---------------------------------------------------------------- 归属与列表

    @Test
    void updateByOtherMemberForbidden() {
        when(productMapper.selectById(1L)).thenReturn(stored(0.0));
        assertThatThrownBy(() -> service.update(1L, FAMILY, 99L, false, new FinanceDTO()))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(ResultCode.FORBIDDEN.getCode()));
    }

    @Test
    void crossFamilyProductTreatedAsMissing() {
        when(productMapper.selectById(1L)).thenReturn(stored(0.0));
        assertThatThrownBy(() -> service.update(1L, 2L, USER, true, new FinanceDTO()))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode()));
        assertThatThrownBy(() -> service.delete(1L, 2L, USER, true))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode()));
    }

    @Test
    void deleteByOwnerAllowedAndLogicalDeleteUsed() {
        when(productMapper.selectById(1L)).thenReturn(stored(0.0));
        service.delete(1L, FAMILY, 99L, true);
        verify(productMapper).deleteById(1L);
    }

    @Test
    void listBackfillsNamesWithSingleBatchQuery() {
        FamilyFinanceProduct a = stored(0.0);
        FamilyFinanceProduct b = stored(0.0);
        b.setId(2L);
        b.setAccountId(null);
        when(productMapper.selectList(any())).thenReturn(List.of(a, b));
        SysUser u = new SysUser();
        u.setId(USER);
        u.setNickname("妈妈");
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(u));
        when(accountMapper.selectBatchIds(any())).thenReturn(List.of(accountOf(FAMILY)));

        List<Map<String, Object>> rows = service.list(FAMILY);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("createdByName")).isEqualTo("妈妈");
        assertThat(rows.get(0).get("accountName")).isEqualTo("招行工资卡");
        assertThat(rows.get(1).get("accountName")).isNull();
        verify(userMapper, times(1)).selectBatchIds(any());
        verify(accountMapper, times(1)).selectBatchIds(any());
    }

    @Test
    void emptyListSkipsBatchQueries() {
        when(productMapper.selectList(any())).thenReturn(List.of());
        assertThat(service.list(FAMILY)).isEmpty();
        verify(userMapper, never()).selectBatchIds(any());
    }
}
