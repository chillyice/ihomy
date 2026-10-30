package com.ihomy.service.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.entity.FamilyLoan;
import com.ihomy.mapper.FamilyLoanMapper;
import com.ihomy.security.SecurityHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 贷款还款日只读来源:按首期还款日逐期推算每期还款日(第 n 期 = 首期 + (n-1) 个月,
 * 日号超出当月天数取月末),期限满即停。与前端 utils/loan.js 的 periodDate 同一口径;
 * 提前还款/利率调整事件只改金额与期数年限,不影响每月还款日,故不参与推算。
 * 展示需 {@code loan:view}(与 /loan/list 一致),无权限时整段跳过。
 */
@Component
@Order(10)
@RequiredArgsConstructor
public class LoanScheduleSource implements ScheduleSourceProvider {

    private final FamilyLoanMapper loanMapper;
    private final SecurityHelper securityHelper;

    @Override
    public String source() {
        return "LOAN";
    }

    @Override
    public boolean available(Long familyId) {
        return familyId != null && securityHelper.hasPermission("loan:view");
    }

    @Override
    public void append(Long familyId, LocalDate from, LocalDate to, List<Map<String, Object>> items) {
        List<FamilyLoan> loans = loanMapper.selectList(new LambdaQueryWrapper<FamilyLoan>()
                .eq(FamilyLoan::getFamilyId, familyId)
                .isNotNull(FamilyLoan::getFirstPayDate)
                .isNotNull(FamilyLoan::getMonths)
                .gt(FamilyLoan::getMonths, 0));
        for (FamilyLoan loan : loans) {
            for (int period = 1; period <= loan.getMonths(); period++) {
                LocalDate due = periodDate(loan.getFirstPayDate(), period);
                if (due.isBefore(from)) continue;
                if (due.isAfter(to)) break;
                Map<String, Object> item = ScheduleSourceProvider.readOnlyItem(
                        source(), loan.getId(), loan.getName(), due, "/book?tab=loan");
                item.put("period", period);
                item.put("periods", loan.getMonths());
                items.add(item);
            }
        }
    }

    /** 第 n 期还款日:首期所在年月 + (n-1) 个月,日号取首期日号与当月天数较小值 */
    static LocalDate periodDate(LocalDate firstPayDate, int period) {
        LocalDate month = firstPayDate.plusMonths(period - 1L);
        int day = Math.min(firstPayDate.getDayOfMonth(), month.lengthOfMonth());
        return month.withDayOfMonth(day);
    }
}
