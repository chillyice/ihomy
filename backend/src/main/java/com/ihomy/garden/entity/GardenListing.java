package com.ihomy.garden.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 花园市集挂单(games.garden_listing):成员互市(§12.2 家庭内循环 + 系统保底)。
 * 挂单即从家庭仓扣走(防双卖);售出 → 货回家庭仓 + 买家币 → 卖家;下架/过期 → 货回仓。
 */
@Data
@TableName("games.garden_listing")
public class GardenListing {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private String sellerUid;
    private String itemCode;
    /** 下单时的物品名快照,防改名后历史错乱 */
    private String itemName;
    private Integer qty;
    private Integer unitPrice;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
}
