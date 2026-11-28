package com.ihomy.garden.dto;

import lombok.Data;

import java.util.List;

/**
 * 拆每日信封响应(FR-27):一天一封按世界算;重复调用幂等(alreadyOpened=true 不重复发奖)。
 */
@Data
public class MailOpenVO {
    private boolean opened = true;
    /** 当天已拆的幂等回执 */
    private boolean alreadyOpened;
    private Long walletCoins;
    private List<OpenedLetter> letters;

    @Data
    public static class OpenedLetter {
        private String date;
        /** FIRST / TEACHING / DAILY */
        private String type;
        private String text;
        private String signature;
        /** 发放的种子包(无则 null) */
        private GardenItemVO seed;
        /** 首封带的园币(无则 0) */
        private Integer coinsGranted;
    }
}
