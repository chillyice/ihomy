package com.ihomy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

/**
 * 小面包房家庭榜上报:前端按「金币 + 面团/面包库存折算」算出的当前净资产。
 * 上限只做防溢出/防手滑,家庭内互信,不做产线回放校验。
 */
@Data
public class BakeryScoreDTO {
    @NotNull(message = "净资产不能为空")
    @PositiveOrZero(message = "净资产不能为负数")
    @Max(value = 1_000_000_000_000_000L, message = "净资产超出可记录范围")
    private Long score;
}
