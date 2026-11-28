package com.ihomy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 小院慢生活:派遣一趟探险(区域、费用、供给、门槛都由服务端按内容表校验)。 */
@Data
public class HomesteadExpeditionDTO {
    @NotBlank(message = "探险区域不能为空")
    @Size(max = 24, message = "区域编码过长")
    private String regionCode;
}
