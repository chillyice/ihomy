package com.ihomy.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 记账周期模板表单:按周期自动生成固定收支(房租/工资/定投等)。
 * 只做支出与收入两种类型;familyId / createdBy / 已生成日与删除时间字段由服务端维护。
 */
@Data
public class BookRecurringDTO {

    /** 类型: EXPENSE 支出 / INCOME 收入 */
    private String type;

    /** 每次金额(元) */
    @NotNull(message = "请填写金额")
    @Digits(integer = 8, fraction = 2, message = "金额超出可记录范围(最多两位小数)")
    private BigDecimal amount;

    /** 分类(选填,空按「其他」) */
    @Size(max = 30, message = "分类最多 30 字")
    private String category;

    /** 账户ID(选填,空=不挂账户) */
    private Long accountId;

    /** 备注(选填) */
    @Size(max = 200, message = "备注最多 200 字")
    private String remark;

    /** 周期: DAILY/WEEKLY/MONTHLY/YEARLY */
    private String period;

    /** 起始日期(周期锚点) */
    @NotNull(message = "请选择起始日期")
    private LocalDate startDate;

    /** 结束日期(含当天,选填) */
    private LocalDate endDate;

    /** 是否启用(新建缺省启用;编辑时不传表示不改) */
    private Integer enabled;
}
