package com.ihomy.garden.dto;

import lombok.Data;

import java.util.List;

/**
 * 批量播种(一畦齐播):一次确认播同种,逐盆校验归属/占用/解锁并分别扣费、分别记流水。
 * plotIds 为空盆地块 ID(只允许本人个人地块与共享地块);确认时逐盆重新校验(§4.2.3)。
 */
@Data
public class BatchSowRequest {
    private String cropCode;
    private List<Long> plotIds;
    /** 可选寄语(≤200 字,随每块流水落 message) */
    private String message;
}
