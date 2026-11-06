package com.ihomy.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 贷款还款记账同步表单:前端按还款流水算出「已到期且该期有还款账户」的期次,
 * 由后端按期次补插记账支出((loan_id, period) 唯一,重复提交不会重复记账)。
 *
 * 金额与日期都由流水引擎(前端 utils/loan.js)给出,后端不复算月供,只做范围与归属校验。
 */
@Data
public class LoanRepaySyncDTO {

    @Valid
    @Size(max = 600, message = "一次最多同步 600 期")
    private List<Item> items;

    @Data
    public static class Item {
        /** 贷款ID */
        @NotNull(message = "缺少贷款ID")
        private Long loanId;
        /** 第 N 期(1 起) */
        @NotNull(message = "缺少期次")
        private Integer period;
        /** 本期还款日 */
        @NotNull(message = "缺少还款日")
        private LocalDate dueDate;
        /** 本期还款额(元;family_book_record.amount 为 DECIMAL(10,2)) */
        @NotNull(message = "缺少还款额")
        @Digits(integer = 8, fraction = 2, message = "还款额超出可记录范围(最多两位小数)")
        private BigDecimal amount;
        /** 本期本金(元) */
        @Digits(integer = 10, fraction = 2, message = "本金超出可记录范围(最多两位小数)")
        private BigDecimal principal;
        /** 本期利息(元) */
        @Digits(integer = 10, fraction = 2, message = "利息超出可记录范围(最多两位小数)")
        private BigDecimal interest;
        /** 扣款账户ID */
        @NotNull(message = "缺少还款账户")
        private Long accountId;
    }
}
