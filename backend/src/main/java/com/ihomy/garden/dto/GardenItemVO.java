package com.ihomy.garden.dto;

import lombok.Data;

/**
 * 仓库条目(种子包 / 果实):种子包 itemCode = SEED_<作物码>,果实 itemCode = 作物码。
 */
@Data
public class GardenItemVO {
    private String itemCode;
    private String cropCode;
    private String itemType;
    private Integer qty;
}
