package com.ihomy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 界面偏好表单:key 决定是哪份偏好(如 home.layout.v3),value 是原样存取的 JSON 文本。
 * 服务端不解析 value 的结构,长度与键格式在这里先挡一层,业务规则(键数上限)在 Service。
 */
@Data
public class UserPrefDTO {

    /** 偏好键:点分小写,版本号写在键里(如 home.layout.v3) */
    @NotBlank(message = "请提供偏好名称")
    @Pattern(regexp = "^[a-z][a-z0-9]*(\\.[a-z0-9]+)*$", message = "偏好名称格式不正确")
    @Size(max = 64, message = "偏好名称过长")
    private String key;

    /** 偏好内容(JSON 文本) */
    @NotBlank(message = "偏好内容不能为空")
    @Size(max = 16384, message = "偏好内容过大")
    private String value;
}
