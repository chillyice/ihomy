package com.ihomy.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求:创建家庭模式填 familyName,加入家庭模式填 inviteCode(二选一)。
 */
@Data
public class RegisterDTO {
    // 注册不再输入用户名/昵称:username 由后端取 email(账号即邮箱),nickname 默认取邮箱前缀,可在个人设置中修改

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 30, message = "密码长度6-30")
    private String password;

    /** 重复输入密码,与 password 一致性由 service 校验 */
    private String confirmPassword;

    /** 注册邮箱 = 登录账号,全局唯一 */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱长度超限")
    private String email;

    /** 图形验证码 ID(由 /auth/captcha 下发) */
    @NotBlank(message = "验证码不能为空")
    private String captchaId;

    /** 图形验证码内容 */
    @NotBlank(message = "验证码不能为空")
    private String captchaCode;

    /** 创建家庭模式:新家庭名称 */
    private String familyName;

    /** 加入家庭模式:邀请码 */
    private String inviteCode;
}
