package com.ihomy.service;

import com.ihomy.common.DictConst;
import com.ihomy.entity.BookAccount;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 记账明细挂账户的口径(纯逻辑,不碰数据库):
 * 收支只认 accountId,转账只认转出/转入且不能同一边、至少一侧;
 * 引用的账户必须都属于本家庭,已删除(查不到)按失效处理。
 */
class BookServiceAccountsTest {

    @Test
    void incomeAndExpenseKeepOnlyAccountId() {
        BookService.AccountRefs refs = BookService.normalizeAccounts(DictConst.BOOK_EXPENSE, 7L, 8L, 9L);
        assertThat(refs.accountId()).isEqualTo(7L);
        assertThat(refs.fromAccountId()).isNull();
        assertThat(refs.toAccountId()).isNull();

        BookService.AccountRefs income = BookService.normalizeAccounts(DictConst.BOOK_INCOME, 7L, null, null);
        assertThat(income.accountId()).isEqualTo(7L);
        assertThat(income.fromAccountId()).isNull();
        assertThat(income.toAccountId()).isNull();
    }

    @Test
    void noAccountMeansOutsideTheLedger() {
        BookService.AccountRefs refs = BookService.normalizeAccounts(DictConst.BOOK_EXPENSE, null, null, null);
        assertThat(refs.accountId()).isNull();
        assertThat(refs.fromAccountId()).isNull();
        assertThat(refs.toAccountId()).isNull();
    }

    @Test
    void transferKeepsOnlyFromAndTo() {
        BookService.AccountRefs refs = BookService.normalizeAccounts(DictConst.BOOK_TRANSFER, 1L, 2L, 3L);
        // 转账误填的收支账户被清掉,避免余额重复计算
        assertThat(refs.accountId()).isNull();
        assertThat(refs.fromAccountId()).isEqualTo(2L);
        assertThat(refs.toAccountId()).isEqualTo(3L);
    }

    @Test
    void transferAllowsOneSideForOutsideMovements() {
        assertThat(BookService.normalizeAccounts(DictConst.BOOK_TRANSFER, null, 2L, null).fromAccountId()).isEqualTo(2L);
        assertThat(BookService.normalizeAccounts(DictConst.BOOK_TRANSFER, null, null, 3L).toAccountId()).isEqualTo(3L);
    }

    @Test
    void transferRejectsSameAccountOnBothSides() {
        assertThatThrownBy(() -> BookService.normalizeAccounts(DictConst.BOOK_TRANSFER, null, 2L, 2L))
                .hasMessageContaining("不能是同一个");
    }

    @Test
    void transferRejectsBothSidesEmpty() {
        assertThatThrownBy(() -> BookService.normalizeAccounts(DictConst.BOOK_TRANSFER, 5L, null, null))
                .hasMessageContaining("至少选择转出或转入账户");
    }

    @Test
    void accountsMustAllBelongToCurrentFamily() {
        List<Long> wanted = List.of(1L, 2L);
        BookService.checkAccountsOwned(100L, wanted, List.of(account(1L, 100L), account(2L, 100L)));

        // 跨家庭:两个都查到了但其中一个属于别的家庭
        assertThatThrownBy(() -> BookService.checkAccountsOwned(100L, wanted,
                List.of(account(1L, 100L), account(2L, 200L))))
                .hasMessageContaining("已失效");

        // 已删除:查不到,数量不够
        assertThatThrownBy(() -> BookService.checkAccountsOwned(100L, wanted, List.of(account(1L, 100L))))
                .hasMessageContaining("已失效");
    }

    private static BookAccount account(Long id, Long familyId) {
        BookAccount a = new BookAccount();
        a.setId(id);
        a.setFamilyId(familyId);
        a.setName("账户" + id);
        return a;
    }
}
