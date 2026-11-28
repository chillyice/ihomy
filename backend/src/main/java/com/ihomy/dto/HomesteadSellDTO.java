package com.ihomy.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 小院慢生活:卖出背包物品换铜板。库存与单价以服务端为准。 */
@Data
public class HomesteadSellDTO {
    @NotBlank(message = "物品不能为空")
    @Size(max = 24, message = "物品编码过长")
    private String itemCode;

    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量至少为 1")
    private Long qty;
}
