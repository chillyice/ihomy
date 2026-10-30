package com.ihomy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 记账本权限表单:家长给某个家庭成员设置账本档位。
 * 档位取值由服务端校验(NONE/READ/WRITE/MANAGE),这里只拦长度与空值。
 */
@Data
public class BookPermissionDTO {

    /** 权限档位: NONE不可见/READ只读/WRITE可记账/MANAGE可管理 */
    @NotBlank(message = "请选择账本权限档位")
    @Size(max = 20, message = "账本权限档位不合法")
    private String level;
}
