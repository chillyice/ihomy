package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 入家申请实体(family_apply):status PENDING/APPROVED/REJECTED,结果经站内通知告知申请人。
 */
@Data
@TableName("family_apply")
public class FamilyApply {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long familyId;
    private String message;
    private String status;
    private Long handledBy;
    private LocalDateTime handledAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
