package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 健康数据上报令牌(sys_health_token):账号级、不属家庭内容。
 * 明文只在生成时返回一次,库里只存 SHA-256 与前 8 位前缀;只够 /health/ingest 与 /health/ping。
 * family_id 是签发时快照,校验时按成员实时解析(与 JWT 快照的差异在此显式说明)。
 */
@Data
@TableName("sys_health_token")
public class SysHealthToken {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long familyId;
    private String name;
    private String tokenHash;
    private String tokenPrefix;
    private LocalDateTime lastUsedAt;
    private LocalDateTime lastIngestAt;
    private Integer revoked;
    private LocalDateTime createdAt;
}
