package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 家庭纪念日实体(family_anniversary):calendar solar/lunar(闰月 isLeap),userId 非空=成员生日。
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
    private Long userId;
    private String recurring;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}