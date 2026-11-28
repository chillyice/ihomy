package com.ihomy.garden.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 花园全景(GET /garden):窗台场景一屏所需的全部数据。
 * 世界摘要与玩家摘要用 Map(与 V1 weather 同风格);地块/仓库/信件为强类型。
 */
@Data
public class GardenPanoramaVO {
    /** {worldId, cycleStartAt, stage, season, seasonName} */
    private Map<String, Object> world;
    /** {playerUid, nickname, coins} */
    private Map<String, Object> player;
    private List<PlotVO> plots;
    /** 种子包(SEED_ 前缀,CONSUMABLE) */
    private List<GardenItemVO> seeds;
    /** 果实与其他存货 */
    private List<GardenItemVO> fruits;
    private Integer inventoryUsed;
    private Integer inventoryCap;
    private List<ShopItemVO> unlockedCrops;
    /** 桌上的信(未拆 + 旧便签) */
    private List<LetterVO> letters;
    /** 猫(FR-36 情绪层):{plotId, venue, slotNo, posture} 或 {plotId:null, venue=area, posture:STROLL};纯函数零存储 */
    private Map<String, Object> cat;
}
