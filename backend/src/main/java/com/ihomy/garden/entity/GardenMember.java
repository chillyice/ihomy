package com.ihomy.garden.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 花园成员档案(games.garden_member):player_uid(ULID)是花园内部标识,
 * sys_user_id 是 ihomy 绑定列——绑定关系只存这一处、单向永不互写(需求设计说明书 §5.3、架构分册 §30.3)。
 */
@Data
@TableName("games.garden_member")
public class GardenMember {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private String playerUid;
    private Long sysUserId;
    private String specialties;
    private LocalDateTime specialtySwitchedAt;
    private LocalDate lastActiveDate;
    private LocalDateTime createdAt;
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
