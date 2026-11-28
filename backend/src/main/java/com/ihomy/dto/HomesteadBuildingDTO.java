package com.ihomy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 小院慢生活:造 / 升一栋场地。花费与材料由服务端按内容表扣。 */
@Data
public class HomesteadBuildingDTO {
    @NotBlank(message = "场地不能为空")
    @Size(max = 24, message = "场地编码过长")
    private String buildingCode;
}
