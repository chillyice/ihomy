package com.ihomy.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 游戏站点账号视图:仅返回给账号所属成员本人,sitePassword 为解密后的明文 */
@Data
public class GameSiteAccountVO {
    private Long gameId;
    private String siteUsername;
    private String sitePassword;
    private String remark;
    private LocalDateTime updatedAt;
}
