package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 家庭文件(family_file):通用文件入口,承接分享收件「存到文件」与文件页上传。
 * 按家庭隔离,软删;列表按上传时间倒序,不参与游客公开。
 */
@Data
@TableName("family_file")
public class FamilyFile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private Long uploaderId;
    private String fileName;
    private String fileUrl;
    private String mimeType;
    private Long fileSize;
    private LocalDateTime createdAt;
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
    private LocalDateTime deletedAt;
}
