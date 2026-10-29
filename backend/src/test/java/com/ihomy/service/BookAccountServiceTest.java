package com.ihomy.service;

import com.ihomy.common.DictConst;
import com.ihomy.entity.BookRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 账户余额口径(纯逻辑,不碰数据库):
 * balance = 期初 + 收入 − 支出 − 转出 + 转入;转账永远不进收支,未挂账户的记录不参与汇总。
 */
class BookAccountServiceTest {

    @Test
    void emptyRecordsGiveEmptySums() {
        assertThat(BookAccountService.sumByAccount(List.of())).isEmpty();
    }

    @Test
    void expenseAndIncomeAggregateOnTheirAccount() {
        Map<Long, BookAccountService.AccountSum> sums = BookAccountService.sumByAccount(List.of(
                record(DictConst.BOOK_INCOME, "20000.00", 1L, null, null),
                record(DictConst.BOOK_EXPENSE, "8659.50", 1L, null, null),
                record(DictConst.BOOK_EXPENSE, "100.00", 2L, null, null)));

        BookAccountService.AccountSum card = sums.get(1L);
        assertThat(card.income()).isEqualByComparingTo("20000.00");
        assertThat(card.expense()).isEqualByComparingTo("8659.50");
        assertThat(card.count()).isEqualTo(2);
        assertThat(card.balance(new BigDecimal("1000.00"))).isEqualByComparingTo("12340.50");

        // 另一个账户只受自己的流水影响
        assertThat(sums.get(2L).expense()).isEqualByComparingTo("100.00");
        assertThat(sums.get(2L).income()).isEqualByComparingTo("0");
        assertThat(sums.get(2L).balance(BigDecimal.ZERO)).isEqualByComparingTo("-100.00");
    }

    @Test
    void transferMovesMoneyWithoutTouchingIncomeOrExpense() {
        Map<Long, BookAccountService.AccountSum> sums = BookAccountService.sumByAccount(List.of(
                record(DictConst.BOOK_TRANSFER, "200.00", null, 1L, 2L)));

        BookAccountService.AccountSum from = sums.get(1L);
        BookAccountService.AccountSum to = sums.get(2L);
        assertThat(from.transferOut()).isEqualByComparingTo("200.00");
        assertThat(from.transferIn()).isEqualByComparingTo("0");
        assertThat(from.income()).isEqualByComparingTo("0");
        assertThat(from.expense()).isEqualByComparingTo("0");
        assertThat(from.balance(new BigDecimal("1000.00"))).isEqualByComparingTo("800.00");

        assertThat(to.transferIn()).isEqualByComparingTo("200.00");
        assertThat(to.transferOut()).isEqualByComparingTo("0");
        assertThat(to.balance(new BigDecimal("0.00"))).isEqualByComparingTo("200.00");

        // 一笔转账在两个账户各计一笔,便于「该账户相关记录数」提示
        assertThat(from.count()).isEqualTo(1);
        assertThat(to.count()).isEqualTo(1);
    }

    @Test
    void oneSidedTransferMeansMoneyLeftOrEnteredOutside() {
        Map<Long, BookAccountService.AccountSum> sums = BookAccountService.sumByAccount(List.of(
                // 买入理财:只填转出账户,钱出了账本
                record(DictConst.BOOK_TRANSFER, "50000.00", null, 3L, null),
                // 赎回:只填转入账户,钱从账本外回来
                record(DictConst.BOOK_TRANSFER, "8000.00", null, null, 4L)));

        assertThat(sums.get(3L).transferOut()).isEqualByComparingTo("50000.00");
        assertThat(sums.get(3L).balance(new BigDecimal("60000.00"))).isEqualByComparingTo("10000.00");
        assertThat(sums.get(4L).transferIn()).isEqualByComparingTo("8000.00");
        assertThat(sums.get(4L).balance(BigDecimal.ZERO)).isEqualByComparingTo("8000.00");
        assertThat(sums).hasSize(2);
    }

    @Test
    void unassignedRecordsStayOutOfEveryAccount() {
        // 历史数据与「账户外」收支:三列都为空,只进总账,不进任何账户余额
        assertThat(BookAccountService.sumByAccount(List.of(
                record(DictConst.BOOK_EXPENSE, "66.00", null, null, null),
                record(DictConst.BOOK_INCOME, "88.00", null, null, null),
                record(DictConst.BOOK_TRANSFER, "99.00", null, null, null)))).isEmpty();
    }

    @Test
    void transferIgnoresAccountIdColumn() {
        // 转账只认转出/转入账户;误填 accountId 也不会被当成收支算进去
        Map<Long, BookAccountService.AccountSum> sums = BookAccountService.sumByAccount(List.of(
                record(DictConst.BOOK_TRANSFER, "300.00", 5L, null, null)));

        assertThat(sums).isEmpty();
    }

    @Test
    void sumsStartAtZeroAndAccumulateMultipleRecords() {
        Map<Long, BookAccountService.AccountSum> sums = BookAccountService.sumByAccount(List.of(
                record(DictConst.BOOK_INCOME, "100.10", 9L, null, null),
                record(DictConst.BOOK_INCOME, "0.90", 9L, null, null),
                record(DictConst.BOOK_TRANSFER, "50.00", null, 9L, 10L),
                record(DictConst.BOOK_TRANSFER, "25.00", null, 10L, 9L)));

        BookAccountService.AccountSum nine = sums.get(9L);
        assertThat(nine.income()).isEqualByComparingTo("101.00");
        assertThat(nine.transferOut()).isEqualByComparingTo("50.00");
        assertThat(nine.transferIn()).isEqualByComparingTo("25.00");
        assertThat(nine.count()).isEqualTo(4);
        assertThat(nine.balance(BigDecimal.ZERO)).isEqualByComparingTo("76.00");
    }

    private static BookRecord record(String type, String amount, Long accountId, Long from, Long to) {
        BookRecord r = new BookRecord();
        r.setType(type);
        r.setAmount(new BigDecimal(amount));
        r.setAccountId(accountId);
        r.setFromAccountId(from);
        r.setToAccountId(to);
        return r;
    }
}
