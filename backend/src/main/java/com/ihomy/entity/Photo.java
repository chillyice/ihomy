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
    private Long sourceFsId;
    private String liveVideoUrl;
    /** 内容 SHA-256(客户端计算,自动备份幂等用;NULL=未参与去重) */
    private String checksum;
    private LocalDateTime createdAt;
    @TableLogic
    private Integer deleted;
    private LocalDateTime deletedAt;
}
