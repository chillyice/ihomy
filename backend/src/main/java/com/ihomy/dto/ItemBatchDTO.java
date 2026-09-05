package com.ihomy.dto;

import lombok.Data;

import java.util.List;

/**
 * 批量设置物品所属家具参数:ids 物品 id 列表;furnitureId 为空时解除归属。
 */
@Data
public class ItemBatchDTO {
    private List<Long> ids;
    private Long furnitureId;
}
