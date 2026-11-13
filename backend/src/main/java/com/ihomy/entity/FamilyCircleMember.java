package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 圈内家庭(family_circle_member):alias 是「本圈内」的称呼,只给同圈其他家庭看。
 * 该表不承载成员名单——圈内看不到对方有哪些人。
 */
@Data
@TableName("family_circle_member")
public class FamilyCircleMember {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long circleId;
    private Long familyId;
    /** 圈内称呼(如「我爸妈」),仅本圈可见 */
    private String alias;
    private Long invitedByFamilyId;
    private LocalDateTime joinedAt;
    /** ACTIVE 在圈 / LEFT 已退出 */
    private String status;
}
