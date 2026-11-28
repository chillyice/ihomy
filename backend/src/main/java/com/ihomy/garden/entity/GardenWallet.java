package com.ihomy.garden.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 个人园币钱包(games.garden_wallet):仓库共享、钱包独立;余额列唯一事实(FR-23)。
 * 园币不碰 ihomy 积分表(NFR-07),积分反向兑换路径不存在。
 */
@Data
@TableName("games.garden_wallet")
public class GardenWallet {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private String playerUid;
    private Long coins;
    private LocalDateTime createdAt;
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
