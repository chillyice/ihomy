package com.ihomy.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 生成健康报表分享快照的入参:分享的是「此刻这份报表」的冻结副本,
 * 不是实时数据通道——所以这里只有卡片清单与敏感开关,没有受众/有效期(固定 7 天)。
 */
@Data
public class HealthShareDTO {
    /** 这份快照包含的卡片 id(空 = 默认五项;未知/重复会被收口) */
    private List<String> cards;
    /** 是否把敏感指标(月经类)也放进快照:默认否,且本人未开同意时强制为否 */
    private Boolean includeSensitive;
    /** 报表锚定日期:空 = 最近一天 */
    private LocalDate date;
}
