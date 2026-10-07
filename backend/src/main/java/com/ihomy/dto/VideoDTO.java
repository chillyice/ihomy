package com.ihomy.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 视频表单:豆瓣式元数据(类型/题材/评分/导演/演员等)。
 */
@Data
public class VideoDTO {
    @Size(max = 200, message = "片名最多 200 字")
    private String title;
    @Size(max = 200, message = "原名最多 200 字")
    private String originalTitle;
    @Size(max = 20, message = "媒体类型取值不正确")
    private String mediaType;
    @Size(max = 200, message = "题材过多")
    private String genres;
    @Size(max = 100, message = "地区最多 100 字")
    private String region;
    private Integer year;
    @Size(max = 100, message = "语言最多 100 字")
    private String language;
    private Integer duration;
    private Integer episodes;
    @Size(max = 200, message = "导演最多 200 字")
    private String director;
    @Size(max = 500, message = "演员最多 500 字")
    private String actors;
    @Digits(integer = 2, fraction = 1, message = "评分最多一位小数,需在 0 至 99.9 之间")
    private BigDecimal rating;
    private String intro;
    @Size(max = 255, message = "海报地址过长")
    private String poster;
    @Size(max = 500, message = "视频地址过长")
    private String videoUrl;
}
