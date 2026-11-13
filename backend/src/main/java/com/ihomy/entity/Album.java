package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 相册实体(content_photo_album):type public/private,决定照片可见性与游客访问;shareToken 用于混淆分享链接。
 */
@Data
@TableName("content_photo_album")
public class Album {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String type;
    /** 可见范围:PRIVATE/CIRCLE/FAMILY/PUBLIC(type 仍保留给旧客户端与游客分享链接判定) */
    private String visibility;
    private String coverPhotoUrl;
    private String coverUrl;
    private String shareToken;
    private Long familyId;
    private Long parentId;
    private Long sourceDeviceId;
    private String sourcePath;
    /** 自动备份相册标识(android:&lt;设备标识&gt;;NULL=普通相册),同家庭唯一 */
    private String backupKey;
    private String syncStatus;
    private LocalDateTime lastSyncedAt;
    private Long createdBy;
    private LocalDateTime createdAt;
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
    private LocalDateTime deletedAt;
}
