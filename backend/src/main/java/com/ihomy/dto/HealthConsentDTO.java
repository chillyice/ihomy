package com.ihomy.dto;

import lombok.Data;

/**
 * 敏感指标(月经周期类)同意开关:这是隐私授权,服务端为准(sys_user_pref 键 health.consent),
 * 不走本地偏好先行,也不走 /user-pref 的推拉同步。
 */
@Data
public class HealthConsentDTO {
    /** 是否同意采集生殖健康类敏感指标(默认关) */
    private Boolean reproductive;
    /** 关闭时是否一并清除已采集的敏感数据(默认勾选) */
    private Boolean clearData;
}
