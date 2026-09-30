package com.ihomy.dto;

import lombok.Data;

/**
 * 智能家居接入配置(保存/连通测试共用)。
 * token 留空表示保留原令牌(与放映厅引擎配置同款交互);
 * 连通测试时字段留空则回退到库中已保存的值,便于「先测再存」。
 */
@Data
public class IotConfigDTO {
    private String baseUrl;
    private String token;
    private Boolean enabled;
}
