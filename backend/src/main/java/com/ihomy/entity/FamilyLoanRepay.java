package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 贷款还款记账台账(family_loan_repay):一行 = 某笔贷款的某一期已记进记账本的支出。
 *
 * (loan_id, period) 唯一,是「重复同步不重复记账」的唯一保证:前端按流水算出已到期期次后调
 * `/loan/repay/sync`,后端只补插缺的期次。金额/本金/利息是记账当时的快照,贷款改参数或换账户
 * 都不回改历史;record_id 指向生成的 family_book_record,仅作追溯(用户删掉那条流水后本期
 * 仍记为「已记账」,不再自动生成)。台账是系统凭证,不设逻辑删列,也没有单独删除入口。
 */
@Data
@TableName("family_loan_repay")
public class FamilyLoanRepay {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属家庭ID */
    private Long familyId;

    /** 所属贷款ID(family_loan.id) */
    private Long loanId;

    /** 第 N 期(1 起) */
    private Integer period;

    /** 本期还款日 */
    private LocalDate dueDate;

    /** 本期还款额(元) */
    private BigDecimal amount;

    /** 本期本金(元) */
    private BigDecimal principal;

    /** 本期利息(元) */
    private BigDecimal interest;

    /** 扣款账户ID(family_book_account.id) */
    private Long accountId;

    /** 生成的记账流水ID(family_book_record.id) */
    private Long recordId;

    private LocalDateTime createdAt;
}
