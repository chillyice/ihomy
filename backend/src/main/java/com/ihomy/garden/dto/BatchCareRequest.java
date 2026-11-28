package com.ihomy.garden.dto;

import lombok.Data;

import java.util.List;

/**
 * 批量照料(巡园):一次确认同一动作(WATER/SUN/FERTILIZE)施加到所选地块,
 * 逐盆校验归属/冷却/费用;缸内补水(MIST)随生态缸端点开放后再加。
 */
@Data
public class BatchCareRequest {
    /** WATER / SUN / FERTILIZE */
    private String action;
    private List<Long> plotIds;
    private String message;
}
