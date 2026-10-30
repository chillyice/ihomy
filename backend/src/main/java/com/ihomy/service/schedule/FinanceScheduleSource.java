package com.ihomy.service.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.DictConst;
import com.ihomy.entity.FamilyFinanceProduct;
import com.ihomy.mapper.FamilyFinanceProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 理财到期只读来源:持有中且有到期日的持仓,到期日当天出一个只读项。
 * 与记账「理财」页签的 90 天到期横排同一批数据(状态 HOLDING + end_date);
 * 已到期/已赎回/已作废不出现,到期状态由前端派生,这里只按日期摊平。
 * 理财接口本身没有独立权限码(家庭成员均可查看),故登录即可见。
 */
@Component
@Order(20)
@RequiredArgsConstructor
public class FinanceScheduleSource implements ScheduleSourceProvider {

    private final FamilyFinanceProductMapper financeProductMapper;

    @Override
    public String source() {
        return "FINANCE";
    }

    @Override
    public boolean available(Long familyId) {
        return familyId != null;
    }

    @Override
    public void append(Long familyId, LocalDate from, LocalDate to, List<Map<String, Object>> items) {
        List<FamilyFinanceProduct> products = financeProductMapper.selectList(new LambdaQueryWrapper<FamilyFinanceProduct>()
                .eq(FamilyFinanceProduct::getFamilyId, familyId)
                .eq(FamilyFinanceProduct::getStatus, DictConst.FINANCE_HOLDING)
                .between(FamilyFinanceProduct::getEndDate, from, to));
        for (FamilyFinanceProduct p : products) {
            LocalDate end = p.getEndDate();
            if (end == null || end.isBefore(from) || end.isAfter(to)) continue;
            items.add(ScheduleSourceProvider.readOnlyItem(
                    source(), p.getId(), p.getName(), end, "/book?tab=finance"));
        }
    }
}
