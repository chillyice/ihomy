package com.ihomy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 小院慢生活:吃料理换限时 buff。份数只用来延长同一份的时长。 */
@Data
public class HomesteadConsumeDTO {
    @NotBlank(message = "物品不能为空")
    @Size(max = 24, message = "物品编码过长")
    private String itemCode;

    @Min(value = 1, message = "至少吃 1 份")
    @Max(value = 5, message = "一次最多吃 5 份")
    private Integer qty = 1;

    public int qtyOrOne() {
        return qty == null ? 1 : qty;
    }
}
