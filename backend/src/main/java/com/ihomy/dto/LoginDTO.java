package com.ihomy.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求:邮箱(账号)+密码+图形验证码。
 */
@Data
public class LoginDTO {
    /** 登录账号 = 注册邮箱 */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;
    @NotBlank(message = "密码不能为空")
    private String password;
    /** 图形验证码 ID(登录时必填,防撞库/爆破) */
    private String captchaId;
    /** 图形验证码内容 */
    @NotBlank(message = "验证码不能为空")
    private String captchaCode;
}
