package com.ihomy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 家庭公告表单:title 必填;imageUrl/linkUrl/生效日期可空。
 */
@Data
public class AnnouncementDTO {
    @NotBlank(message = "请填写公告标题")
    @Size(max = 100, message = "标题最多 100 字")
    private String title;
    @Size(max = 500, message = "图片地址过长")
    private String imageUrl;
    @Size(max = 500, message = "跳转链接过长")
    private String linkUrl;
    private Integer sortOrder;
    private Integer enabled;
    private LocalDate startDate;
    private LocalDate endDate;
}
