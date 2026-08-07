package com.ihomy.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 博客表单:标题必填,status/visibility 缺省由 service 补默认值。
 */
@Data
public class BlogDTO {
    @NotBlank(message = "标题不能为空")
    private String title;
    private String content;
    private String coverImage;
    private String tags;
    private Integer status;
    private Integer visibility;
}
