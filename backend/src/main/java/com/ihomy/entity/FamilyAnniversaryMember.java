package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 纪念日关联成员(family_anniversary_member):一条纪念日可关联多位家庭成员,
 * 如结婚纪念日关联夫妻双方、家庭日关联全家。唯一键 (anniversary_id, user_id) 防重复。
 * family_id 冗余存放:便于按家庭批量清理与越权校验,避免每次回查主表。
 */
@Data
@TableName("family_anniversary_member")
public class FamilyAnniversaryMember {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long anniversaryId;
    private Long userId;
    private Long familyId;
    private LocalDateTime createdAt;
}
