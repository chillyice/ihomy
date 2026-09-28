package com.ihomy.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 贷款记录表单。events 为事件时间轴(利率调整/提前还款),编辑时整体替换。
 */
@Data
public class FamilyLoanDTO {
    private String name;
    /** 资金渠道: COMMERCIAL/FUND/OTHER */
    private String channel;
    /** 还款方式: EQUAL_INSTALLMENT/EQUAL_PRINCIPAL */
    private String method;
    /** 贷款本金(元) */
    private BigDecimal amount;
    /** 还款期数(月) */
    private Integer months;
    /** 放款日(可空) */
    private LocalDate loanDate;
    /** 首期还款日(可空) */
    private LocalDate firstPayDate;
    /** 首期还款额(元,可空;空 = 按整月口径推算) */
    private BigDecimal firstPayment;
    /** 初始年利率(%) */
    private BigDecimal rate;
    /** 贷款组名称(选填;同组贷款合并展示合计) */
    private String groupName;
    private String note;

    /** 事件时间轴 */
    private List<EventDTO> events;

    @Data
    public static class EventDTO {
        /** 事件类型: RATE_CHANGE/PREPAY */
        private String type;
        /** 生效期次:在第 N 期还款之后生效 */
        private Integer effectivePeriod;
        /** 事件触发日(选填):与生效期次互为表述,前端据此决定重新拖拽时是否重开编辑 */
        private LocalDate triggerDate;
        /** 调整后的年利率(%)(RATE_CHANGE) */
        private BigDecimal rate;
        /** 提前还款金额(元)(PREPAY) */
        private BigDecimal amount;
        /** 提前还款处理: SHORTEN/REDUCE */
        private String strategy;
        private String note;
    }
}
