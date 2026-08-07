package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 首页模块实体(sys_home_module):familyId 为空=全局模块;category 用于侧栏分组展示。
 */
@Data
@TableName("sys_home_module")
public class HomeModule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String title;
    private String icon;
    private String path;
    private String category;
    private String position;
    private Integer sortOrder;
    private Integer enabled;
    private Long familyId;
    private LocalDateTime createdAt;
}
