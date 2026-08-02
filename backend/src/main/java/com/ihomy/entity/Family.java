package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_family_info")
public class Family {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String coverImage;
    private String coverText;
    private String coverSubtitle;
    private Long ownerId;
    private Integer isDefault;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
