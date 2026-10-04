package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 家庭纪念日实体(family_anniversary):calendar solar/lunar(闰月 isLeap),每年重复。
 * 关联成员为多对多(V10.11 起),见 family_anniversary_member;无关联=家庭级纪念日。
 */
@Data
@TableName("family_anniversary")
public class Anniversary {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String calendar;
    private Integer month;
    private Integer day;
    private Integer isLeap;
    private Long familyId;
    private String recurring;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}