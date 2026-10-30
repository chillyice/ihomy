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
 * V10.92 起带来源三列:从放映厅/纪念日/小游戏/家庭计划许愿时记录来源快照。
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
    /** 来源类型:NULL=直接添加,取值见 DictConst.WISH_SOURCE_* */
    private String sourceType;
    /** 来源记录ID(字符串,媒体引擎条目ID是 GUID) */
    private String sourceId;
    /** 来源标题快照(来源被删仍可显示) */
    private String sourceTitle;
    private String status;
    private Long requesterId;
    private String visibility;
    /** 达成时间:改为待实现时需清空,故强制更新(NULL 也写库) */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime achievedAt;
    private LocalDateTime createdAt;
}