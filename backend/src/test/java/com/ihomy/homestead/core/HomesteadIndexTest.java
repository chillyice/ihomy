package com.ihomy.homestead.core;

import com.ihomy.homestead.content.HomesteadContent;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 小院指数:等级分 + 铜板 + 库存半价,服务端唯一口径。 */
class HomesteadIndexTest {

    private static final HomesteadContent CONTENT = new HomesteadContent();

    @Test
    void levelsCoinsAndHalfPriceInventory() {
        // 一个 3 级技能 = 3000;铜板 500;松枝 10 × 2 半价 = 10
        long score = HomesteadIndex.compute(
                Map.of("WOOD", 3), 500, Map.of("PINE_TWIG", 10L), CONTENT);
        assertEquals(3510, score);
    }

    @Test
    void emptyPlayerIsZero() {
        assertEquals(0, HomesteadIndex.compute(Map.of(), 0, Map.of(), CONTENT));
    }

    @Test
    void ignoresUnknownItemsAndNegativeQty() {
        assertEquals(0, HomesteadIndex.compute(Map.of(), 0,
                Map.of("NOPE", 100L, "PINE_TWIG", -5L), CONTENT));
    }
}
