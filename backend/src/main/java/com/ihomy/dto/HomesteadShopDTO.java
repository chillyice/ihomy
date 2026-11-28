package com.ihomy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 小院慢生活:用探险积分兑换宝箱或物品。 */
@Data
public class HomesteadShopDTO {
    @NotBlank(message = "兑换项不能为空")
    @Size(max = 32, message = "兑换项编码过长")
    private String shopCode;
}
