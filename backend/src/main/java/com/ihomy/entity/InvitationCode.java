package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 邀请码实体(family_invitation_code):预设角色 presetRoleId,次数上限 maxUses,过期时间 expiresAt。
 */
@Data
@TableName("family_invitation_code")
public class InvitationCode {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private Long familyId;
    private Long presetRoleId;
    private Integer maxUses;
    private Integer usedCount;
    private LocalDateTime expiresAt;
    private String status;
    private Long createdBy;
    private LocalDateTime createdAt;
}
