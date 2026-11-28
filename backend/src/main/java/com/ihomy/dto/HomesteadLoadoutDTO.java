package com.ihomy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 小院慢生活:换装预设(保存 / 应用 / 购买)。序号 1–5,前两套免费。 */
@Data
public class HomesteadLoadoutDTO {
    @NotNull(message = "预设序号不能为空")
    @Min(value = 1, message = "预设序号从 1 开始")
    @Max(value = 5, message = "最多 5 套预设")
    private Integer index;

    @Size(max = 24, message = "预设名过长")
    private String name;

    public int indexOrOne() {
        return index == null ? 1 : index;
    }
}
