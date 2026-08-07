package com.ihomy.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 视频表单:豆瓣式元数据(类型/题材/评分/导演/演员等)。
 */
@Data
public class VideoDTO {
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
}
