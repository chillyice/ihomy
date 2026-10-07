package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 家庭曲库曲目实体(content_music):source_path 为设备映射去重键(dev:设备ID:路径),
 * 本地上传/外链曲目为空;sync_status 记映射状态 VALID/OFFLINE/MISSING。
 * 字段单位(时长秒、码率 kbps)与列级说明见 schema.sql 的 content_music 建表注释。
 */
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
