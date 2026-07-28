package com.family.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("home_module")
public class HomeModule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String title;
    private String icon;
    private String path;
    private String position;
    private Integer sortOrder;
    private Integer enabled;
    private Long familyId;
    private LocalDateTime createdAt;
}
