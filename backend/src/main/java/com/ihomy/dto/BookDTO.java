package com.ihomy.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 记账表单:type 取 EXPENSE 支出 / INCOME 收入 / TRANSFER 转账(由 DictConst.bookType 归一)。
 * 账户三列:收支填 accountId,转账填 fromAccountId/toAccountId;均可空(空=账户外,不影响统计)。
 */
@Data
public class BookDTO {
    private String type;
    @Digits(integer = 8, fraction = 2, message = "金额超出可记录范围(最多两位小数)")
    private BigDecimal amount;
    @Size(max = 30, message = "分类最多 30 字")
    private String category;
    @Size(max = 200, message = "备注最多 200 字")
    private String remark;
    private LocalDate recordDate;
    /** 收支账户(支出=付款账户/收入=收款账户) */
    private Long accountId;
    /** 转账转出账户(可空=转出到账户外,如买入理财/取现) */
    private Long fromAccountId;
    /** 转账转入账户(可空=从账户外转入,如赎回/存现) */
    private Long toAccountId;
}
