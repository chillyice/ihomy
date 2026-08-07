package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * "想看"实体(content_video_wish):status 0待入库/1已入库,软删删除。
 */
@Data
@TableName("content_video_wish")
public class VideoWish {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String genres;
    private String reason;
    private Integer status;
    private Long requesterId;
    private Long familyId;
    private LocalDateTime createdAt;
    private Integer deleted;
}
