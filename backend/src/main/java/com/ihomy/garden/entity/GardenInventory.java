package com.ihomy.garden.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 家庭公共仓库(games.garden_inventory):按 (family_id, item_code) 同码堆叠,qty=0 即删行;
 * 个人地块的收成也入这一个仓,钱包独立(FR-13/FR-15)。种子包以 SEED_ 前缀、CONSUMABLE 类型入库。
 */
@Data
@TableName("games.garden_inventory")
public class GardenInventory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private String itemCode;
    private String itemType;
    private Integer qty;
    private LocalDateTime createdAt;
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
