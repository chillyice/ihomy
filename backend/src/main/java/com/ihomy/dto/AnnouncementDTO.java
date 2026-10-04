package com.ihomy.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 家庭公告表单:title 必填;imageUrl/linkUrl/生效日期可空。
 */
@Data
public class AnnouncementDTO {
    private String title;
    private String imageUrl;
    private String linkUrl;
    private Integer sortOrder;
    private Integer enabled;
    private LocalDate startDate;
    private LocalDate endDate;
}
