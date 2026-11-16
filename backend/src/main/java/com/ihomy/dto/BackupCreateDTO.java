package com.ihomy.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 手动备份请求:kind=BASIC/CONTENT/FULL/MIGRATION。 */
@Data
public class BackupCreateDTO {

    @NotBlank(message = "备份类型不能为空")
    private String kind;
}
