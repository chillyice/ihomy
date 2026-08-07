package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 入家申请实体(sys_family_apply):status 0待审核/1通过/2拒绝,结果经站内通知告知申请人。
 */
@Data
@TableName("sys_family_apply")
public class FamilyApply {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long familyId;
    private String message;
    private Integer status;
    private Long handledBy;
    private LocalDateTime handledAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
