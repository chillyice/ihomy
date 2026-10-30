package com.ihomy.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ihomy.common.BizException;
import com.ihomy.common.DictConst;
import com.ihomy.dto.BookRecurringDTO;
import com.ihomy.entity.BookAccount;
import com.ihomy.entity.BookRecord;
import com.ihomy.entity.BookRecurring;
import com.ihomy.mapper.BookAccountMapper;
import com.ihomy.mapper.BookRecordMapper;
import com.ihomy.mapper.BookRecurringMapper;
import com.ihomy.mapper.SysUserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 周期模板生成与表单规则(纯逻辑,不起 Spring/DB):
 * 抢占式幂等(重复跑不重复插)、生成流水字段来自模板、只收收支两种类型、账户必须属于本家庭。
 * 「抢占 + 插流水」的事务原子性由真实容器的 TransactionOperations 保证,这里只验证行为分支。
 */
class BookRecurringServiceTest {

    private static final Long FAMILY = 3L;
    private static final Long USER = 7L;
    private static final Long ACCOUNT = 9L;

    private final BookRecurringMapper recurringMapper = mock(BookRecurringMapper.class);
    private final BookRecordMapper bookMapper = mock(BookRecordMapper.class);
    private final BookAccountMapper accountMapper = mock(BookAccountMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    /** 单测里没有事务容器:直接执行回调,真实环境由 TransactionTemplate 提交/回滚 */
    private final TransactionOperations tx = new TransactionOperations() {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(null);
        }
    };
    private final BookRecurringService service =
            new BookRecurringService(recurringMapper, bookMapper, accountMapper, userMapper, tx);

    /** 单测没有 MyBatis-Plus 启动扫描,手动注册实体信息,否则 LambdaWrapper 的列名解析会报错 */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), BookRecurring.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), BookRecord.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), BookAccount.class);
    }

    private BookRecurring dailyTemplate() {
        BookRecurring r = new BookRecurring();
        r.setId(11L);
        r.setFamilyId(FAMILY);
        r.setType(DictConst.BOOK_EXPENSE);
        r.setAmount(new BigDecimal("3000.00"));
        r.setCategory("家居");
        r.setAccountId(ACCOUNT);
        r.setRemark("房租");
        r.setPeriod("DAILY");
        r.setStartDate(LocalDate.now());
        r.setEnabled(1);
        r.setCreatedBy(USER);
        return r;
    }

    @Test
    void rerunOnSameDayDoesNotInsertTwice() {
        BookRecurring row = dailyTemplate();
        when(recurringMapper.selectList(any(Wrapper.class))).thenReturn(List.of(row));
        // 第一次抢到「已生成日」并插入;第二次条件更新命中 0 行(另一执行已推进/已生成)
        when(recurringMapper.update(isNull(), any())).thenReturn(1, 0);

        int first = service.generateForFamily(FAMILY);
        int second = service.generateForFamily(FAMILY);

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        verify(bookMapper, times(1)).insert(any(BookRecord.class));
    }

    @Test
    void generatedRecordCopiesTemplateAndUsesOccurrenceDate() {
        BookRecurring row = dailyTemplate();
        when(recurringMapper.selectList(any(Wrapper.class))).thenReturn(List.of(row));
        when(recurringMapper.update(isNull(), any())).thenReturn(1);

        service.generateForFamily(FAMILY);

        ArgumentCaptor<BookRecord> captor = ArgumentCaptor.forClass(BookRecord.class);
        verify(bookMapper).insert(captor.capture());
        BookRecord r = captor.getValue();
        assertThat(r.getFamilyId()).isEqualTo(FAMILY);
        assertThat(r.getType()).isEqualTo(DictConst.BOOK_EXPENSE);
        assertThat(r.getAmount()).isEqualByComparingTo("3000.00");
        assertThat(r.getCategory()).isEqualTo("家居");
        assertThat(r.getAccountId()).isEqualTo(ACCOUNT);
        assertThat(r.getRemark()).isEqualTo("房租");
        assertThat(r.getCreatedBy()).isEqualTo(USER);
        assertThat(r.getRecordDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void pausedTemplateIsNotScanned() {
        // selectList 已按 enabled=1 过滤;返回空表示无启用模板
        when(recurringMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        assertThat(service.generateForFamily(FAMILY)).isZero();
        verify(bookMapper, never()).insert(any(BookRecord.class));
    }

    @Test
    void transferAndNonPositiveAmountAreRejected() {
        BookRecurringDTO transfer = new BookRecurringDTO();
        transfer.setType(DictConst.BOOK_TRANSFER);
        transfer.setAmount(new BigDecimal("10"));
        transfer.setStartDate(LocalDate.now());
        assertThatThrownBy(() -> service.create(USER, FAMILY, transfer))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("只支持支出和收入");

        BookRecurringDTO zero = new BookRecurringDTO();
        zero.setType(DictConst.BOOK_EXPENSE);
        zero.setAmount(BigDecimal.ZERO);
        zero.setStartDate(LocalDate.now());
        assertThatThrownBy(() -> service.create(USER, FAMILY, zero))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("金额必须大于 0");
    }

    @Test
    void accountMustBelongToTheFamily() {
        BookRecurringDTO dto = new BookRecurringDTO();
        dto.setType(DictConst.BOOK_EXPENSE);
        dto.setAmount(new BigDecimal("100"));
        dto.setStartDate(LocalDate.now());
        dto.setAccountId(ACCOUNT);

        BookAccount foreign = new BookAccount();
        foreign.setId(ACCOUNT);
        foreign.setFamilyId(99L);
        foreign.setName("别人家的账户");
        when(accountMapper.selectById(ACCOUNT)).thenReturn(foreign);

        assertThatThrownBy(() -> service.create(USER, FAMILY, dto))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("账户已失效");
    }

    @Test
    void endDateBeforeStartDateIsRejected() {
        BookRecurringDTO dto = new BookRecurringDTO();
        dto.setType(DictConst.BOOK_EXPENSE);
        dto.setAmount(new BigDecimal("100"));
        dto.setStartDate(LocalDate.of(2026, 10, 1));
        dto.setEndDate(LocalDate.of(2026, 9, 30));

        assertThatThrownBy(() -> service.create(USER, FAMILY, dto))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("结束日期不能早于起始日期");
    }
}
