package com.ihomy.homestead.core;

import com.ihomy.homestead.HomesteadConst;
import com.ihomy.homestead.content.HomesteadContent;
import com.ihomy.homestead.content.ItemDef;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 赶集日行情(纯函数,价格唯一口径)。
 * <p>
 * 每天从素材、成品里各挑一样「镇上收」的货,按 {@link HomesteadConst#MARKET_BONUS_BP} 溢价,
 * 当天两单共享 {@link HomesteadConst#MARKET_QUOTA} 件额度,额度用完回到内容表原价。
 * 选货只看日期(按编码排序后按 epochDay 取模轮转),不取时钟、不用随机数:
 * 同一天所有端算出的行情一模一样,离线补算与单测都能精确复现。
 */
public final class HomesteadMarket {

    private HomesteadMarket() {
    }

    /** 一条收购行情(basePrice 留给前端展示涨幅,quota 是当天共享额度) */
    public record Order(String itemCode, long price, long basePrice, int quota) {

        /** 还剩多少件可以按行情价收(used 为当天已用额度) */
        public int remaining(int used) {
            return Math.max(0, quota - Math.max(0, used));
        }
    }

    /** 一次卖货的拆价:命中行情的部分按行情价,其余按原价 */
    public record Sale(long bonusQty, long normalQty, long bonusCoins, long normalCoins, int usedAfter) {

        public long coins() {
            return bonusCoins + normalCoins;
        }
    }

    /** 当天行情:素材、成品各一样(池子为空则少一单) */
    public static List<Order> orders(LocalDate date, HomesteadContent content) {
        List<Order> out = new ArrayList<>(2);
        addOrder(out, date, content, ItemDef.CAT_MATERIAL);
        addOrder(out, date, content, ItemDef.CAT_PRODUCT);
        return List.copyOf(out);
    }

    /** 行情价:ceil(原价 × 溢价基点 / 10000),整数口径不引入浮点 */
    public static long price(long basePrice) {
        return (basePrice * HomesteadConst.MARKET_BONUS_BP + HomesteadConst.BP_BASE - 1)
                / HomesteadConst.BP_BASE;
    }

    /**
     * 卖货拆价。
     *
     * @param orders 当天行情
     * @param used   当天已用额度(存档日期不是今天时传 0)
     * @param item   卖的是什么
     * @param qty    卖多少(> 0)
     */
    public static Sale plan(List<Order> orders, int used, ItemDef item, long qty) {
        Order hit = null;
        for (Order order : orders) {
            if (order.itemCode().equals(item.code())) {
                hit = order;
                break;
            }
        }
        int usedSafe = Math.max(0, used);
        long bonusQty = hit == null ? 0L : Math.min(qty, hit.remaining(usedSafe));
        long normalQty = Math.max(0L, qty - bonusQty);
        long bonusPrice = hit == null ? 0L : hit.price();
        return new Sale(
                bonusQty,
                normalQty,
                bonusPrice * bonusQty,
                item.sellPrice() * normalQty,
                (int) (usedSafe + bonusQty));
    }

    private static void addOrder(List<Order> out, LocalDate date, HomesteadContent content, String category) {
        List<ItemDef> pool = content.items().stream()
                .filter(item -> category.equals(item.category()))
                .sorted(Comparator.comparing(ItemDef::code))
                .toList();
        if (pool.isEmpty()) {
            return;
        }
        ItemDef pick = pool.get((int) Math.floorMod(date.toEpochDay(), (long) pool.size()));
        out.add(new Order(pick.code(), price(pick.sellPrice()), pick.sellPrice(), HomesteadConst.MARKET_QUOTA));
    }
}
