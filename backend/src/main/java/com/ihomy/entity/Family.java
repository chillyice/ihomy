package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 家庭实体(sys_family_info):is_public 控制公开搜索/访客可见,shareToken 用于混淆分享链接。
 */
@Data
@TableName("sys_family_info")
public class Family {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String coverImage;
    private String coverText;
    private String coverSubtitle;
    private String description;
    private Integer isPublic;
    private Long ownerId;
    private Integer isDefault;
    private Integer isDemo;
    /** 16 位混淆分享 token,公开访问 URL 用 ?hid= 而非裸 ID */
    private String shareToken;
    /** 家庭背景音乐:URL(本地上传 /files/... 或外链) */
    private String musicUrl;
    private String musicTitle;
    /** 天气/太阳位置偏好(空=IP自动定位) */
    private java.math.BigDecimal weatherLat;
    private java.math.BigDecimal weatherLng;
    private String weatherCity;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
