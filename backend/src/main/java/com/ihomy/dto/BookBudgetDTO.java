package com.ihomy.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 记账预算表单:一个支出分类一条月度预算,金额与备注可改、分类也可改(改后仍需唯一)。
 * familyId / createdBy / 删除与时间字段由服务端填,不走表单。
 */
@Data
public class BookBudgetDTO {

    /** 支出分类(与记账分类同名,如 餐饮/交通) */
    @NotBlank(message = "请选择支出分类")
    @Size(max = 30, message = "分类最多 30 字")
    private String category;

    /** 每月预算金额(元) */
    @NotNull(message = "请填写预算金额")
    @Digits(integer = 8, fraction = 2, message = "预算金额超出可记录范围(最多两位小数)")
    private BigDecimal amount;

    /** 备注(选填) */
    @Size(max = 200, message = "备注最多 200 字")
    private String note;
}
