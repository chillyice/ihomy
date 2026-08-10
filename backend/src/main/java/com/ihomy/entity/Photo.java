package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 照片实体(content_photo):归属相册与家庭,可见性随相册类型(public→PUBLIC,private→FAMILY)。
 */
@Data
@TableName("content_photo")
public class Photo {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long albumId;
    private String url;
    private String description;
    private java.time.LocalDateTime takenAt;
    private String location;
    private Integer likeCount;
    private Long authorId;
    private Long familyId;
    private String visibility;
    private String sourcePath;
    private LocalDateTime createdAt;
    @TableLogic
    private Integer deleted;
}
