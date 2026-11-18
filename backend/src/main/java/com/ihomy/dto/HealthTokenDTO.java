package com.ihomy.dto;

import lombok.Data;

/** 健康数据上报令牌:生成只传设备名;吊销走路径 id。 */
@Data
public class HealthTokenDTO {
    /** 设备名,如「iPhone 快捷指令」,最长 50 */
    private String name;
}
