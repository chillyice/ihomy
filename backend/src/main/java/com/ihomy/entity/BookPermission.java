package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 记账本权限实体(family_book_permission):家长按成员配置整本账本的可见与读写档位。
 *
 * 只有显式配置过的成员才有行;没有行 = 默认「可记账」(WRITE),维持历史口径。
 * 家长(OWNER)不受本表约束,恒为可管理;等级为 NONE/READ/WRITE/MANAGE,见 DictConst.BOOK_PERM_*。
 */
@Data
@TableName("family_book_permission")
public class BookPermission {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属家庭ID */
    private Long familyId;

    /** 被授权的家庭成员ID */
    private Long userId;

    /** 权限等级: NONE不可见/READ只读/WRITE可记账/MANAGE可管理 */
    private String permLevel;

    /** 配置人ID(家长) */
    private Long createdBy;

    private LocalDateTime createdAt;

    @TableField(updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
