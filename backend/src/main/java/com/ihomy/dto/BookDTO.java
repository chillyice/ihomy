package com.ihomy.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 记账表单:type 0支出 1收入 2转账。
 */
@Data
public class BookDTO {
    private Integer type;
    private BigDecimal amount;
    private String category;
    private String remark;
    private LocalDate recordDate;
}