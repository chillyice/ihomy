package com.ihomy.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 小院慢生活:按实例 id 操作一件装备(强化 / 分解 / 重铸)。归属由服务端校验。 */
@Data
public class HomesteadGearIdDTO {
    @NotNull(message = "装备不能为空")
    private Long gearId;
}
