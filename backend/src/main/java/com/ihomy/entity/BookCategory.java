package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 书架分类实体(content_book_category):parentId 自关联成树,同级按 sortOrder 排序。
 * 注意与关系表 content_book_category_rel 的分工——分类树在本表,书↔分类归属在关系表(一本书可属多类)。
 */
@Data
@TableName("content_book_category")
public class BookCategory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Long parentId;
    private Long familyId;
    private Integer sortOrder;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableLogic
    private Integer deleted;
}
