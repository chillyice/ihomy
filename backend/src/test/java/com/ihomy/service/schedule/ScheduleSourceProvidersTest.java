package com.ihomy.service.schedule;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ihomy.entity.FamilyFinanceProduct;
import com.ihomy.entity.FamilyLoan;
import com.ihomy.entity.Item;
import com.ihomy.mapper.FamilyFinanceProductMapper;
import com.ihomy.mapper.FamilyLoanMapper;
import com.ihomy.mapper.ItemMapper;
import com.ihomy.mapper.NotificationMapper;
import com.ihomy.mapper.SysUserMapper;
import com.ihomy.service.ItemExpiryService;
import com.ihomy.service.NotificationService;
import com.ihomy.security.SecurityHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 日程只读来源:贷款还款日逐期推算、理财到期判定、食材到期口径与 ItemExpiryService 一致。
 * 纯逻辑断言(不起 Spring 上下文)。
 */
class ScheduleSourceProvidersTest {

    private static final Long FAMILY = 2L;

    private final FamilyLoanMapper loanMapper = mock(FamilyLoanMapper.class);
    private final SecurityHelper securityHelper = mock(SecurityHelper.class);
    private final LoanScheduleSource loanSource = new LoanScheduleSource(loanMapper, securityHelper);

    private final FamilyFinanceProductMapper financeMapper = mock(FamilyFinanceProductMapper.class);
    private final FinanceScheduleSource financeSource = new FinanceScheduleSource(financeMapper);

    private final ItemMapper itemMapper = mock(ItemMapper.class);
    private final ItemExpiryService itemExpiryService = new ItemExpiryService(
            itemMapper, mock(NotificationMapper.class), mock(NotificationService.class), mock(SysUserMapper.class));
    private final ItemExpiryScheduleSource itemSource = new ItemExpiryScheduleSource(itemMapper, itemExpiryService);

    /** 单测没有 MyBatis-Plus 启动扫描,手动注册实体信息,否则 LambdaWrapper 的列名解析会报错 */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, FamilyLoan.class);
        TableInfoHelper.initTableInfo(assistant, FamilyFinanceProduct.class);
        TableInfoHelper.initTableInfo(assistant, Item.class);
    }

    private FamilyLoan loan(Long id, String name, LocalDate firstPayDate, int months) {
        FamilyLoan l = new FamilyLoan();
        l.setId(id);
        l.setFamilyId(FAMILY);
        l.setName(name);
        l.setFirstPayDate(firstPayDate);
        l.setMonths(months);
        return l;
    }

    private FamilyFinanceProduct product(Long id, String name, LocalDate endDate, String status) {
        FamilyFinanceProduct p = new FamilyFinanceProduct();
        p.setId(id);
        p.setFamilyId(FAMILY);
        p.setName(name);
        p.setEndDate(endDate);
        p.setStatus(status);
        return p;
    }

    private Item ingredient(Long id, String name, LocalDateTime storedAt, Integer shelfLife, String unit) {
        Item it = new Item();
        it.setId(id);
        it.setFamilyId(FAMILY);
        it.setName(name);
        it.setType("INGREDIENT");
        it.setStoredAt(storedAt);
        it.setShelfLife(shelfLife);
        it.setShelfLifeUnit(unit);
        return it;
    }

    // ---------------------------------------------------------------- 贷款还款日

    @Test
    void loanPeriodDateKeepsAnchorDayAndClampsToMonthEnd() {
        LocalDate first = LocalDate.of(2026, 1, 31);
        assertThat(LoanScheduleSource.periodDate(first, 1)).isEqualTo(LocalDate.of(2026, 1, 31));
        assertThat(LoanScheduleSource.periodDate(first, 2)).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(LoanScheduleSource.periodDate(first, 3)).isEqualTo(LocalDate.of(2026, 3, 31));
        // 跨年后 2 月仍按首期日号取月末
        assertThat(LoanScheduleSource.periodDate(first, 14)).isEqualTo(LocalDate.of(2027, 2, 28));
    }

    @Test
    void loanPaymentsExpandedOnlyWithinRangeAndTerm() {
        when(securityHelper.hasPermission("loan:view")).thenReturn(true);
        when(loanMapper.selectList(any())).thenReturn(List.of(loan(1L, "建行房贷", LocalDate.of(2026, 10, 15), 3)));

        List<Map<String, Object>> items = new ArrayList<>();
        assertThat(loanSource.available(FAMILY)).isTrue();
        loanSource.append(FAMILY, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31), items);

        assertThat(items).extracting(m -> m.get("date"))
                .containsExactly("2026-10-15", "2026-11-15", "2026-12-15");
        assertThat(items).extracting(m -> m.get("period")).containsExactly(1, 2, 3);
        assertThat(items.get(0)).containsEntry("kind", "LOAN")
                .containsEntry("source", "LOAN")
                .containsEntry("readOnly", true)
                .containsEntry("link", "/book?tab=loan")
                .containsEntry("title", "建行房贷")
                .containsEntry("periods", 3);
    }

    @Test
    void loanStopsAfterTermAndSkipsOutsideRange() {
        when(securityHelper.hasPermission("loan:view")).thenReturn(true);
        when(loanMapper.selectList(any())).thenReturn(List.of(loan(1L, "车贷", LocalDate.of(2026, 10, 15), 2)));

        List<Map<String, Object>> onlyNovember = new ArrayList<>();
        loanSource.append(FAMILY, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), onlyNovember);
        assertThat(onlyNovember).hasSize(1);
        assertThat(onlyNovember.get(0)).containsEntry("period", 2);

        List<Map<String, Object>> afterTerm = new ArrayList<>();
        loanSource.append(FAMILY, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 31), afterTerm);
        assertThat(afterTerm).isEmpty();
    }

    @Test
    void loanSourceHiddenWithoutLoanViewPermission() {
        when(securityHelper.hasPermission("loan:view")).thenReturn(false);
        assertThat(loanSource.available(FAMILY)).isFalse();
        assertThat(loanSource.available(null)).isFalse();
    }

    // ---------------------------------------------------------------- 理财到期

    @Test
    void financeMaturityFallsOnEndDate() {
        when(financeMapper.selectList(any())).thenReturn(List.of(
                product(1L, "招行大额存单", LocalDate.of(2026, 11, 20), "HOLDING"),
                product(2L, "活期宝", null, "HOLDING"),
                product(3L, "已赎回理财", LocalDate.of(2026, 7, 1), "REDEEMED")));

        List<Map<String, Object>> items = new ArrayList<>();
        assertThat(financeSource.available(FAMILY)).isTrue();
        financeSource.append(FAMILY, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), items);

        assertThat(items).hasSize(1);
        assertThat(items.get(0)).containsEntry("kind", "FINANCE")
                .containsEntry("title", "招行大额存单")
                .containsEntry("date", "2026-11-20")
                .containsEntry("link", "/book?tab=finance")
                .containsEntry("readOnly", true);
    }

    @Test
    void financeOutsideRangeSkipped() {
        when(financeMapper.selectList(any())).thenReturn(List.of(
                product(1L, "明年到期", LocalDate.of(2027, 3, 1), "HOLDING")));

        List<Map<String, Object>> items = new ArrayList<>();
        financeSource.append(FAMILY, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), items);

        assertThat(items).isEmpty();
    }

    // ---------------------------------------------------------------- 食材到期

    @Test
    void ingredientExpiryUsesSameShelfLifeMathAsNotification() {
        when(itemMapper.selectList(any())).thenReturn(List.of(
                ingredient(1L, "牛奶", LocalDateTime.of(2026, 10, 1, 8, 0), 3, "DAY"),
                ingredient(2L, "面包", LocalDateTime.of(2026, 10, 2, 10, 0), 12, "HOUR")));

        List<Map<String, Object>> items = new ArrayList<>();
        assertThat(itemSource.available(FAMILY)).isTrue();
        itemSource.append(FAMILY, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), items);

        assertThat(items).extracting(m -> m.get("title") + "@" + m.get("date"))
                .containsExactly("牛奶@2026-10-04", "面包@2026-10-02");
        assertThat(items.get(0)).containsEntry("kind", "ITEM_EXPIRY")
                .containsEntry("source", "ITEM_EXPIRY")
                .containsEntry("link", "/kitchen/ingredients")
                .containsEntry("readOnly", true);
    }

    @Test
    void ingredientWithoutShelfLifeIsNotScheduled() {
        when(itemMapper.selectList(any())).thenReturn(List.of(
                ingredient(3L, "没有保质期", LocalDateTime.of(2026, 10, 1, 8, 0), null, null)));

        List<Map<String, Object>> items = new ArrayList<>();
        itemSource.append(FAMILY, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), items);

        assertThat(items).isEmpty();
    }

    @Test
    void ingredientExpiryDateMatchesServiceExpiry() {
        Item milk = ingredient(1L, "牛奶", LocalDateTime.of(2026, 10, 1, 8, 0), 1, "MONTH");
        // 月按 30 天近似(与过期预警同一口径)
        assertThat(itemExpiryService.expiryDate(milk)).isEqualTo(LocalDate.of(2026, 10, 31));
    }
}
