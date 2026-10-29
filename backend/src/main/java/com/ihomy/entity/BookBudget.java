package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 家庭记账预算实体(family_book_budget):按支出分类给一个月度预算额度。
 *
 * 预算金额每月复用同一份,不按月存多行;当月已花由 family_book_record 实时汇总,
 * 剩余/超支与进度都不落库(前端 utils/bookLedger.js 与后端 BookBudgetService 同一口径)。
 */
@Data
@TableName("family_book_budget")
public class BookBudget {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属家庭ID */
    private Long familyId;

    /** 支出分类(与记账明细的 category 同名) */
    private String category;

    /** 每月预算金额(元) */
    private BigDecimal amount;

    /** 备注 */
    private String note;

    /** 创建人ID */
    private Long createdBy;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;

    @TableField(updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
