package com.ihomy.garden.dto;

import lombok.Data;

/**
 * 首页便签状态(FR-32 ①「有话可说」的判定):有未拆的信 / 有地块待照料 / 平静。
 * 便签露出与否由 ihomy 侧(game_info / sys_home_module)决定;本接口只回答「有没有话」。
 */
@Data
public class GardenEntryVO {
    /** 世界可进入(登录成员已引导建号) */
    private boolean enabled = true;
    /** 当前成员在世界内的标识 */
    private String playerUid;
    /** MAIL 有信 / CARE 待照料 / CALM 平静(优先级 MAIL > CARE > CALM) */
    private String note;
    /** 桌上有未拆的信(last_mail_date < 今天) */
    private boolean hasUnopenedMail;
    /** 有已种地块缺水或已可收 */
    private boolean needsCare;
    /** 有已种地块可以收获 */
    private boolean harvestReady;
    /** 是否已有植物在种(false = 首次窗台) */
    private boolean hasPlanted;
}
