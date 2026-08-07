package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 相册实体(content_album):type public/private,决定照片可见性与游客访问。
 */
@Data
@TableName("content_album")
public class Album {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String type;
    private String coverPhotoUrl;
    private Long familyId;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
