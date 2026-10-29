package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 记账明细实体(family_book_record):家庭共享账本,收支与转账记录。type: EXPENSE/INCOME/TRANSFER。
 */
@Data
@TableName("family_book_record")
public class BookRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private String type;
    private BigDecimal amount;
    private String category;
    private String remark;
    private LocalDate recordDate;
    private Long createdBy;
    /** 收支账户ID(EXPENSE=付款账户 / INCOME=收款账户;可空=未归属账户) */
    private Long accountId;
    /** 转出账户ID(TRANSFER 用;可空=转到账户外,如买入理财/取现) */
    private Long fromAccountId;
    /** 转入账户ID(TRANSFER 用;可空=账户外转入,如赎回/存现) */
    private Long toAccountId;
    private LocalDateTime createdAt;
}