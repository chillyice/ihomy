package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 密码重置令牌实体(sys_password_reset_token):一次性 token,30 分钟过期,used=1 即失效。
 */
@Data
@TableName("sys_password_reset_token")
public class PasswordResetToken {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String token;
    private LocalDateTime expiresAt;
    private Integer used;
    private LocalDateTime createdAt;
}
