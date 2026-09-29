package com.ihomy.dto;

import lombok.Data;

/**
 * 放映厅媒体引擎配置(保存/连通测试共用)。
 * 保存时 password 留空表示保留原密码(与百度网盘凭证同款交互);
 * 连通测试时字段留空则回退到库中已保存的值,便于「先测再存」。
 */
@Data
public class MediaServerDTO {
    private String serverType;
    private String serverUrl;
    private String publicUrl;
    private String username;
    private String password;
    private Boolean enabled;
}
