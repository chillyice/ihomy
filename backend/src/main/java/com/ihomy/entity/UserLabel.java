package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 身份标签实体(family_user_label, V3.9):家庭成员在某一家庭内的身份标签,
 * 预设"爸爸/妈妈",其余为自定义字符串,每用户每家庭一条。
 */
@Data
@TableName("family_user_label")
public class UserLabel {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long familyId;
    private String label;
    private String color;
    private LocalDateTime createdAt;
}