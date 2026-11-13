package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 亲友圈(family_circle):家庭之间的「可分享关系」容器,发起家庭即圈管理员。
 * 入圈不共享任何内容,圈只是发布内容时的可选受众。
 */
@Data
@TableName("family_circle")
public class FamilyCircle {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String coverImage;
    /** 发起家庭(可改圈名/邀请/移除/解散) */
    private Long ownerFamilyId;
    /** 邀请码(可重置,过期即失效) */
    private String inviteCode;
    private LocalDateTime inviteExpiresAt;
    /** 家庭数上限(含发起家庭) */
    private Integer maxFamilies;
    /** ACTIVE / CLOSED */
    private String status;
    private Long createdBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
