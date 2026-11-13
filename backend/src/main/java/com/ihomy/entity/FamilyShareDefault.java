package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 家庭默认分享目标(family_share_default):发布内容选「亲友圈」时预勾选哪些圈内家庭。
 * 只做预选,不改变「默认只给自家人」的规则。
 */
@Data
@TableName("family_share_default")
public class FamilyShareDefault {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private Long targetFamilyId;
    private Long createdBy;
    private LocalDateTime createdAt;
}
