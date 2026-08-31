package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("content_music")
public class ContentMusic {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private String url;
    private String title;
    private String artist;
    private String album;
    private Integer duration;
    private Integer bitrate;
    private String coverUrl;
    private String sourcePath;
    private Long sourceDeviceId;
    private Long sourceFsId;
    private String sourceDir;
    private String syncStatus;
    private Long addedBy;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createdAt;
}
