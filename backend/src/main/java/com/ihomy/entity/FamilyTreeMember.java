package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 家谱成员实体(family_tree):father/mother/spouse 自关联构成血缘+婚姻树,
 * generation 从祖先(0)向下递增,用于世代视图分组。
 */
@Data
@TableName("family_tree")
public class FamilyTreeMember {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private String name;
    /** 性别: 0未知 1男 2女 */
    private Integer gender;
    private LocalDate birthDate;
    private String photo;
    private Long fatherId;
    private Long motherId;
    private Long spouseId;
    /** 世代: 0=第一代(祖先),每代+1 */
    private Integer generation;
    private String note;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}