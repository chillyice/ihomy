package com.ihomy.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 记账表单:type 0支出 1收入 2转账。
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
}
