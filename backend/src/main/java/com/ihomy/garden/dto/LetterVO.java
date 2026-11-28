package com.ihomy.garden.dto;

import lombok.Data;

/**
 * 桌上的信(全景下发):claimable=true 可拆可补(FR-27 最多补 3 天);
 * claimable=false 为旧便签(可读无奖励,text 确定性推导不落库)。
 * 未拆的信不带 text——拆信必须走 POST /garden/mail/open(奖励在那一步落库)。
 */
@Data
public class LetterVO {
    private String date;
    /** FIRST / TEACHING / DAILY */
    private String type;
    private boolean claimable;
    /** 旧便签才带正文 */
    private String text;
    private String signature;
}
