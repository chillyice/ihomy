package com.ihomy.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** 游戏站点账号保存请求:用户名/密码至少填一项,密码入库前 AES-GCM 加密 */
@Data
public class GameSiteAccountDTO {
    @Size(max = 100, message = "用户名不能超过 100 字")
    private String siteUsername;

    @Size(max = 200, message = "密码不能超过 200 字")
    private String sitePassword;

    @Size(max = 200, message = "备注不能超过 200 字")
    private String remark;
}
