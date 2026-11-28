package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 小院慢生活背包(game_homestead_item):一人一物品一行,只保留见过的物品。
 * 数量为 0 的行保留(卖出不清行),省一次删除与重建;上限由服务端截断(MAX_ITEM_QTY)。
 */
@Data
@TableName("game_homestead_item")
public class HomesteadItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String itemCode;
    private Long qty;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
