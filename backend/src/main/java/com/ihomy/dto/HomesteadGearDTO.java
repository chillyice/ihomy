package com.ihomy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 小院慢生活:穿戴升一阶。花费与材料由服务端按内容表扣。 */
@Data
public class HomesteadGearDTO {
    @NotBlank(message = "部位不能为空")
    @Size(max = 16, message = "部位编码过长")
    private String slotCode;
}
