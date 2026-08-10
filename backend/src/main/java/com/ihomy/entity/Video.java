package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 放映厅视频实体(content_video):豆瓣式元数据(类型/题材/评分等),软删删除。
 */
@Data
@TableName("content_video")
public class Video {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String originalTitle;
    private String mediaType;
    private String genres;
    private String region;
    private Integer year;
    private String language;
    private Integer duration;
    private Integer episodes;
    private String director;
    private String actors;
    private BigDecimal rating;
    private String intro;
    private String poster;
    private String videoUrl;
    private Long uploaderId;
    private Long familyId;
    private String visibility;
    private LocalDateTime createdAt;
    private Integer deleted;
}
