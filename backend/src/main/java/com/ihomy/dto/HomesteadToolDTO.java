package com.ihomy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 小院慢生活:制造 / 升级工具。花费与材料由服务端按内容表扣,tier 0→1 即制造。 */
@Data
public class HomesteadToolDTO {
    @NotBlank(message = "工具不能为空")
    @Size(max = 24, message = "工具编码过长")
    private String toolCode;
}
