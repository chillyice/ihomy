package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 百度网盘接入凭证实体(sys_baidu_credential):家庭级四件套 AppID/AppKey/SecretKey/SignKey;
 * SecretKey/SignKey/accessToken/refreshToken ENC 加密存储,不回传前端。
 * OAuth 授权码模式:回调页 {前端origin}/storage/baidu/callback,须与百度开放平台注册的「授权回调页地址」一致。
 */
@Data
@TableName("sys_baidu_credential")
public class BaiduCredential {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private String appId;
    private String appKey;
    private String secretKey;
    private String signKey;
    private String accessToken;
    private String refreshToken;
    private LocalDateTime tokenExpiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
