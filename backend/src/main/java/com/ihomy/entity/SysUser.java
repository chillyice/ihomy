package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体(sys_user):登录账号即注册邮箱(唯一),角色经 sys_user_role 关联表按家庭解析。
 */
@Data
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    /** 密码哈希,禁止序列化回前端 */
    @JsonIgnore
    private String password;
    private String nickname;
    private String avatar;
    private String email;
    private java.time.LocalDate birthday;
    private Integer gender;
    private Long familyId;
    /** 用户设置的默认家庭(多家庭时优先访问,空=主家庭 familyId) */
    private Long defaultFamilyId;
    private Integer status;
    private Integer isFake;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
