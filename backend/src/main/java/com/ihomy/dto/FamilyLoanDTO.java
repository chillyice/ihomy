package com.ihomy.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 贷款记录表单。events 为事件时间轴(利率调整/提前还款),编辑时整体替换。
 *
 * 长度上限按列宽、金额精度按 DECIMAL(14,2) 前置拦截(超限原先落库抛异常走兜底 500);
 * rate 的边界由 LoanRecordService.validRate 按 DECIMAL(6,4) 单独校验。
 */
@Data
public class FamilyLoanDTO {
    @Size(max = 100, message = "贷款名称过长")
    private String name;
    /** 资金渠道: COMMERCIAL/FUND/OTHER */
    private String channel;
    /** 还款方式: EQUAL_INSTALLMENT/EQUAL_PRINCIPAL */
    private String method;
    /** 贷款本金(元) */
    @Digits(integer = 12, fraction = 2, message = "贷款本金超出可记录范围(最多两位小数)")
    private BigDecimal amount;
    /** 还款期数(月) */
    private Integer months;
    /** 放款日(可空) */
    private LocalDate loanDate;
    /** 首期还款日(可空) */
    private LocalDate firstPayDate;
    /** 首期还款额(元,可空;空 = 按整月口径推算) */
    @Digits(integer = 12, fraction = 2, message = "首期还款额超出可记录范围(最多两位小数)")
    private BigDecimal firstPayment;
    /** 初始年利率(%) */
    private BigDecimal rate;
    /** 贷款组名称(选填;同组贷款合并展示合计) */
    @Size(max = 100, message = "贷款组名称过长")
    private String groupName;
    @Size(max = 500, message = "备注过长")
    private String note;

    /** 事件时间轴 */
    @Valid
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
        @Digits(integer = 12, fraction = 2, message = "提前还款金额超出可记录范围(最多两位小数)")
        private BigDecimal amount;
        /** 提前还款处理: SHORTEN/REDUCE */
        private String strategy;
        @Size(max = 500, message = "备注过长")
        private String note;
    }
}
