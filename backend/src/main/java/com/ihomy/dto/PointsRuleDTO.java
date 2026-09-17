package com.ihomy.dto;

import lombok.Data;

/**
 * 积分获取规则保存入参(批量):家长配置某功能的开关与分值。
 * enabled 缺省视为开启;points 缺省回退该功能默认分值。
 */
@Data
public class PointsRuleDTO {
    private String featureCode;
    private Boolean enabled;
    private Integer points;
}
