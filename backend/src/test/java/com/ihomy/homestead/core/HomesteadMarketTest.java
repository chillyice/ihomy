package com.ihomy.homestead.core;

import com.ihomy.homestead.HomesteadConst;
import com.ihomy.homestead.content.HomesteadContent;
import com.ihomy.homestead.content.ItemDef;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 赶集日行情:每天两单(素材 + 成品)按日期轮转、溢价取整、当天共享额度的拆价。
 * 行情只依赖「日期 + 内容表」,所以这里不 mock 任何时钟或随机源。
 */
class HomesteadMarketTest {

    private static final HomesteadContent CONTENT = new HomesteadContent();
    private static final LocalDate DAY = LocalDate.of(2026, 11, 28);

    @Test
    void ordersAreOneMaterialAndOneProduct() {
        List<HomesteadMarket.Order> orders = HomesteadMarket.orders(DAY, CONTENT);
        assertEquals(2, orders.size());
        assertEquals(ItemDef.CAT_MATERIAL, CONTENT.item(orders.get(0).itemCode()).category());
        assertEquals(ItemDef.CAT_PRODUCT, CONTENT.item(orders.get(1).itemCode()).category());
    }

    @Test
    void ordersRotateWithDateAndRepeatWithSameDate() {
        List<HomesteadMarket.Order> first = HomesteadMarket.orders(DAY, CONTENT);
        List<HomesteadMarket.Order> again = HomesteadMarket.orders(DAY, CONTENT);
        assertEquals(codes(first), codes(again), "同一天必须算出同一份行情");

        List<HomesteadMarket.Order> next = HomesteadMarket.orders(DAY.plusDays(1), CONTENT);
        assertNotEquals(codes(first), codes(next), "次日应换货");
        assertEquals(codes(first), codes(HomesteadMarket.orders(DAY, new HomesteadContent())),
                "行情不能依赖内容实例的顺序");
    }

    @Test
    void ordersStayInsideTheirPools() {
        for (int offset = 0; offset < 64; offset++) {
            LocalDate day = DAY.plusDays(offset);
            for (HomesteadMarket.Order order : HomesteadMarket.orders(day, CONTENT)) {
                assertTrue(CONTENT.item(order.itemCode()) != null, "行情引用了未知物品:" + order.itemCode());
                assertEquals(HomesteadConst.MARKET_QUOTA, order.quota());
                assertEquals(order.price(), HomesteadMarket.price(order.basePrice()));
            }
        }
    }

    @Test
    void priceIsCeilingOfBonus() {
        assertEquals(3, HomesteadMarket.price(2));      // 2 × 1.5 = 3
        assertEquals(8, HomesteadMarket.price(5));      // 7.5 向上取整
        assertEquals(21, HomesteadMarket.price(14));    // 21 整
        assertEquals(180, HomesteadMarket.price(120));  // 180 整
    }

    @Test
    void saleSplitsQuotaThenFallsBackToBasePrice() {
        HomesteadMarket.Order order = HomesteadMarket.orders(DAY, CONTENT).get(0);
        ItemDef item = CONTENT.item(order.itemCode());

        HomesteadMarket.Sale sale = HomesteadMarket.plan(HomesteadMarket.orders(DAY, CONTENT), 0, item, 100);
        assertEquals(HomesteadConst.MARKET_QUOTA, sale.bonusQty());
        assertEquals(100 - HomesteadConst.MARKET_QUOTA, sale.normalQty());
        assertEquals(order.price() * HomesteadConst.MARKET_QUOTA, sale.bonusCoins());
        assertEquals(item.sellPrice() * (100 - HomesteadConst.MARKET_QUOTA), sale.normalCoins());
        assertEquals(order.price() * HomesteadConst.MARKET_QUOTA
                + item.sellPrice() * (100 - HomesteadConst.MARKET_QUOTA), sale.coins());
        assertEquals(HomesteadConst.MARKET_QUOTA, sale.usedAfter());
    }

    @Test
    void quotaIsSharedBetweenTheTwoOrders() {
        List<HomesteadMarket.Order> orders = HomesteadMarket.orders(DAY, CONTENT);
        ItemDef other = CONTENT.item(orders.get(1).itemCode());

        HomesteadMarket.Sale sale = HomesteadMarket.plan(orders, HomesteadConst.MARKET_QUOTA, other, 10);
        assertEquals(0, sale.bonusQty(), "两单共用额度,用完后回到原价");
        assertEquals(other.sellPrice() * 10, sale.coins());
        assertEquals(HomesteadConst.MARKET_QUOTA, sale.usedAfter());
    }

    @Test
    void itemsOutsideTodayOrdersKeepBasePrice() {
        Set<String> ordered = codes(HomesteadMarket.orders(DAY, CONTENT));
        ItemDef plain = CONTENT.items().stream()
                .filter(item -> !ordered.contains(item.code()))
                .findFirst()
                .orElseThrow();

        HomesteadMarket.Sale sale = HomesteadMarket.plan(HomesteadMarket.orders(DAY, CONTENT), 0, plain, 7);
        assertEquals(0, sale.bonusQty());
        assertEquals(plain.sellPrice() * 7, sale.normalCoins());
        assertEquals(0, sale.usedAfter(), "非行情货不占额度");
    }

    private static Set<String> codes(List<HomesteadMarket.Order> orders) {
        return orders.stream().map(HomesteadMarket.Order::itemCode).collect(Collectors.toSet());
    }
}
