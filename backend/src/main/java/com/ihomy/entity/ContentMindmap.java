package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 脑图(content_mindmap):工具箱-脑图设计。
 * 家庭级共享,全家庭成员可查看编辑;data 为前端 simple-mind-map 的完整 JSON(getData(true))。
 */
@Data
@TableName("content_mindmap")
public class ContentMindmap {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long familyId;

    /** 创建人ID */
    private Long userId;

    private String title;

    /** 脑图数据 JSON(layout/root/theme/view/config) */
    private String data;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
