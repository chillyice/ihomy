package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 小面包房家庭榜(game_bakery_idle_score):一人一行,记录最近一次上报的当前净资产。
 * 净值会因升级花费回落,所以是覆盖更新;榜单查询以成员身份为准(退家庭后不再出现)。
 */
@Data
@TableName("game_bakery_idle_score")
public class GameBakeryScore {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private Long userId;
    /** 当前净资产(金币 + 面团/面包库存折算),前端按同一口径计算后上报 */
    private Long score;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
