package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 愿望单实体(content_wish):家庭共享愿望,提出/标记达成/放弃。
 */
@Data
@TableName("content_wish")
public class Wish {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private String title;
    private String reason;
    private String category;
    private String status;
    private Long requesterId;
    private String visibility;
    /** 达成时间:改为待实现时需清空,故强制更新(NULL 也写库) */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime achievedAt;
    private LocalDateTime createdAt;
}