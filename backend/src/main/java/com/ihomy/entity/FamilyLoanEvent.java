package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 贷款事件(family_loan_event):贷款的事件时间轴 —— 利率调整 / 提前还款。
 *
 * 事件在第 N 期还款之后生效(effective_period = N):利率调整 → 第 N+1 期起按新利率计息;
 * 提前还款 → 第 N 期后减少剩余本金(金额 ≥ 剩余本金即结清)。
 * 触发日(trigger_date)可选,只作原始录入日期的留存与重新拖拽时的日期确认口径,还款流水不读它。
 * 子表不带 family_id(家庭隔离由主记录保证);编辑贷款时事件整体重写,故不设逻辑删列,
 * 删除走 XML 物理 DELETE。
 */
@Data
@TableName("family_loan_event")
public class FamilyLoanEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属贷款ID(family_loan.id) */
    private Long loanId;

    /** 生效期次:在第 N 期还款之后生效(1 = 首期还款后) */
    private Integer effectivePeriod;

    /** 事件触发日(选填):与生效期次互为表述;填了日期的记录,前端在流水线上重新拖拽时会弹编辑重新确认日期 */
    private LocalDate triggerDate;

    /** 事件类型: RATE_CHANGE 利率调整 / PREPAY 提前还款 */
    private String type;

    /** 调整后的年利率(%)(RATE_CHANGE 时有效) */
    private BigDecimal rate;

    /** 提前还款金额(元)(PREPAY 时有效) */
    private BigDecimal amount;

    /** 提前还款处理: SHORTEN 缩短年限(月供不变) / REDUCE 减少月供(期限不变) */
    private String strategy;

    /** 备注 */
    private String note;

    private LocalDateTime createdAt;
}
