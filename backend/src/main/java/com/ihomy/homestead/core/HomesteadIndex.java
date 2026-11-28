package com.ihomy.homestead.core;

import com.ihomy.homestead.HomesteadConst;
import com.ihomy.homestead.content.HomesteadContent;

import java.util.Map;

/**
 * 小院指数(家庭榜口径,服务端唯一计算处):
 * 技能等级 × 1000 + 铜板 + Σ 库存数量 × 卖价 × 50%。
 * 库存按半价计,避免「囤满原料」把榜单堆高而不做加工。
 */
public final class HomesteadIndex {

    private HomesteadIndex() {
    }

    public static long compute(Map<String, Integer> levelBySkill,
                               long coins,
                               Map<String, Long> qtyByItem,
                               HomesteadContent content) {
        long score = 0;
        if (levelBySkill != null) {
            for (Integer level : levelBySkill.values()) {
                if (level != null && level > 0) {
                    score += (long) level * HomesteadConst.INDEX_LEVEL_POINTS;
                }
            }
        }
        score += Math.max(0, coins);
        if (qtyByItem != null) {
            for (Map.Entry<String, Long> entry : qtyByItem.entrySet()) {
                Long qty = entry.getValue();
                if (qty == null || qty <= 0) {
                    continue;
                }
                long price = content.sellPrice(entry.getKey());
                score += qty * price * HomesteadConst.INDEX_ITEM_SELL_BP / HomesteadConst.SPEED_BASE_BP;
            }
        }
        return Math.max(0, score);
    }
}
