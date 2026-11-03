package com.ihomy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 保险箱查看密码的二次校验表单:password 为当前账号的登录密码,
 * 只用于本次校验,不落库、不进操作日志(request_params 为空)。
 */
@Data
public class VaultRevealDTO {

    @NotBlank(message = "请输入登录密码")
    @Size(max = 128, message = "密码过长")
    private String password;
}
