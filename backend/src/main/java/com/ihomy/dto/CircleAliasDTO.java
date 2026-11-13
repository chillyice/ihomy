package com.ihomy.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** 圈内称呼:本家庭在这个圈里叫什么(「我爸妈」「岳父岳母」),只对同圈家庭可见。 */
@Data
public class CircleAliasDTO {

    @Size(max = 30, message = "称呼最多 30 个字")
    private String alias;

    /** 空=清除称呼;传入空串同样按清除处理(MP 的 updateById 会跳过 null 字段) */
    public String normalized() {
        return alias == null || alias.isBlank() ? null : alias.trim();
    }
}
