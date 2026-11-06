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

    private BookRecord recordOn(LocalDate date) {
        BookRecord r = new BookRecord();
        r.setFamilyId(FAMILY);
        r.setType(DictConst.BOOK_EXPENSE);
        r.setAmount(new BigDecimal("3000.00"));
        r.setCategory("家居");
        r.setRecordDate(date);
        return r;
    }

    @Test
    void manualRunSkipsPeriodsAlreadyRecorded() {
        BookRecurring row = dailyTemplate();
        when(recurringMapper.selectList(any(Wrapper.class))).thenReturn(List.of(row));
        // 今天已有同类型同分类的一笔(上次生成过 / 用户手记过):这一期算已记过,不再插入
        when(bookMapper.selectList(any(Wrapper.class))).thenReturn(List.of(recordOn(LocalDate.now())));

        assertThat(service.backfillForFamily(FAMILY)).isZero();
        verify(bookMapper, never()).insert(any(BookRecord.class));
    }

    @Test
    void generatedRecordCopiesTemplateAndUsesOccurrenceDate() {
        BookRecurring row = dailyTemplate();
        when(recurringMapper.selectList(any(Wrapper.class))).thenReturn(List.of(row));

        service.backfillForFamily(FAMILY);

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
    void createBackfillsHistoryFromStartDate() {
        // 起始日两个月前 → 保存时就应生成 3 笔(而不是等次日的定时任务)
        BookRecurringDTO dto = new BookRecurringDTO();
        dto.setType(DictConst.BOOK_EXPENSE);
        dto.setAmount(new BigDecimal("3000"));
        dto.setCategory("家居");
        dto.setRemark("房租");
        dto.setPeriod("MONTHLY");
        dto.setStartDate(LocalDate.now().minusMonths(2));
        when(recurringMapper.update(isNull(), any())).thenReturn(1);

        var created = service.create(USER, FAMILY, dto);

        assertThat(created.get("generated")).isEqualTo(3);
        verify(bookMapper, times(3)).insert(any(BookRecord.class));
    }

    @Test
    void manualRunBackfillsFullHistoryNotJust90Days() {
        // 立即生成本来就不套定时任务的 90 天窗口:5 个月前的月度模板应补 6 笔
        BookRecurring row = dailyTemplate();
        row.setPeriod("MONTHLY");
        row.setStartDate(LocalDate.now().minusMonths(5));
        when(recurringMapper.selectList(any(Wrapper.class))).thenReturn(List.of(row));

        int generated = service.backfillForFamily(FAMILY);

        assertThat(generated).isEqualTo(6);
        verify(bookMapper, times(6)).insert(any(BookRecord.class));
    }

    @Test
    void manualRunIgnoresLastGeneratedDateAndFillsHistory() {
        // 「已生成日」已经很近(旧版本推进过),但起始日更早:历史缺口仍要补出来
        BookRecurring row = dailyTemplate();
        row.setPeriod("MONTHLY");
        row.setStartDate(LocalDate.now().minusMonths(5));
        row.setLastGeneratedDate(LocalDate.now().minusMonths(1)); // 只差最近 1 期的话,以前只补 1 笔
        when(recurringMapper.selectList(any(Wrapper.class))).thenReturn(List.of(row));

        assertThat(service.backfillForFamily(FAMILY)).isEqualTo(6);
        verify(bookMapper, times(6)).insert(any(BookRecord.class));
        // 补完把已生成日推到最后一期,定时任务接着往后生成
        assertThat(row.getLastGeneratedDate()).isAfterOrEqualTo(LocalDate.now().withDayOfMonth(1));
    }

    @Test
    void editingNeverGeneratedTemplateBackfillsHistory() {
        // 旧模板从未生成过(lastGeneratedDate 为空):编辑保存时补历史;已生成过的模板只影响后续
        BookRecurring row = dailyTemplate();
        row.setPeriod("MONTHLY");
        row.setStartDate(LocalDate.now().minusMonths(2));
        when(recurringMapper.selectById(11L)).thenReturn(row);
        when(recurringMapper.update(isNull(), any())).thenReturn(1);

        BookRecurringDTO dto = new BookRecurringDTO();
        dto.setAmount(new BigDecimal("3200"));
        int generated = service.update(11L, FAMILY, USER, true, dto);

        assertThat(generated).isEqualTo(3);
        verify(bookMapper, times(3)).insert(any(BookRecord.class));

        row.setLastGeneratedDate(LocalDate.now().minusMonths(1));
        assertThat(service.update(11L, FAMILY, USER, true, dto)).isZero();
    }

    @Test
    void pausedTemplateIsNotScanned() {
        // selectList 已按 enabled=1 过滤;返回空表示无启用模板
        when(recurringMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        assertThat(service.backfillForFamily(FAMILY)).isZero();
        verify(bookMapper, never()).insert(any(BookRecord.class));
    }

    @Test
    void manualRunKeepsHandEditedAmountAsAlreadyRecorded() {
        // 用户核对账单后手改了金额:分类仍对得上,视为这一期已记过,不再插一笔(避免重复记账)
        BookRecurring row = dailyTemplate();
        when(recurringMapper.selectList(any(Wrapper.class))).thenReturn(List.of(row));
        BookRecord edited = recordOn(LocalDate.now());
        edited.setAmount(new BigDecimal("2800.00"));
        when(bookMapper.selectList(any(Wrapper.class))).thenReturn(List.of(edited));

        assertThat(service.backfillForFamily(FAMILY)).isZero();
        verify(bookMapper, never()).insert(any(BookRecord.class));
    }

    @Test
    void coveredCheckTrustsAmountOnlyTogetherWithAccount() {
        // 只比金额会把「同一天恰好花了同样的钱」的别的支出当成这一期,金额必须与账户一起比
        BookRecurring row = dailyTemplate();
        when(recurringMapper.selectList(any(Wrapper.class))).thenReturn(List.of(row));

        service.backfillForFamily(FAMILY);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<BookRecord>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(bookMapper).selectList(captor.capture());
        assertThat(captor.getValue().getSqlSegment())
                .contains("category")
                .contains("amount")
                .contains("account_id");
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
