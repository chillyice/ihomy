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
    private LocalDateTime createdAt;
}