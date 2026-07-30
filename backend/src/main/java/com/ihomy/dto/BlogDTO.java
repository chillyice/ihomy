package com.ihomy.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BlogDTO {
    @NotBlank(message = "标题不能为空")
    private String title;
    private String content;
    private String coverImage;
    private Integer status;
    private Integer visibility;
}
