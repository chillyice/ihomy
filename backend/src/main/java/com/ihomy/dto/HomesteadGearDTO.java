package com.ihomy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 小院慢生活:制造 / 强化装备。花费与材料由服务端按内容表扣。 */
@Data
public class HomesteadGearDTO {
    @NotBlank(message = "装备不能为空")
    @Size(max = 24, message = "装备编码过长")
    private String gearCode;
}
