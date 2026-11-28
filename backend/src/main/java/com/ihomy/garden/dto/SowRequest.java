package com.ihomy.garden.dto;

import lombok.Data;

/**
 * 播种入参(FR-04):个人地块不传 ownerUid——归属只认会话身份,不接受入参覆盖。
 */
@Data
public class SowRequest {
    private String venue;
    private Integer slotNo;
    private String cropCode;
    /** 成员寄语(可选,进成长日志) */
    private String message;
}
