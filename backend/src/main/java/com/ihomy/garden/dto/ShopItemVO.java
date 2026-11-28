package com.ihomy.garden.dto;

import lombok.Data;

/**
 * 可种作物条目(播种器数据):解锁不看季节(FR-28),inSeason 仅用于排序与「当季」标记。
 */
@Data
public class ShopItemVO {
    private String code;
    private String name;
    private String form;
    private Integer seedPrice;
    private Integer fruitPrice;
    private boolean inSeason;
    /** 当季提示(春/夏/秋/冬;全年作物为空) */
    private String seasonName;
    private boolean firstPot;
    /** 手里已有的种子包数(播种器排序用) */
    private Integer packCount;
}
