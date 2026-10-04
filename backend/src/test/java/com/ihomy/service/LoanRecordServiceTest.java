package com.ihomy.service;

import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.FamilyLoanDTO;
import com.ihomy.mapper.FamilyLoanEventMapper;
import com.ihomy.mapper.FamilyLoanMapper;
import com.ihomy.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * 年利率边界:与 rate 列 DECIMAL(6,4) 对齐,越界输入抛 400 而不是落库炸约束走 500。
 */
class LoanRecordServiceTest {

    private final FamilyLoanMapper loanMapper = mock(FamilyLoanMapper.class);
    private final FamilyLoanEventMapper eventMapper = mock(FamilyLoanEventMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final LoanRecordService service = new LoanRecordService(loanMapper, eventMapper, userMapper);

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
}
