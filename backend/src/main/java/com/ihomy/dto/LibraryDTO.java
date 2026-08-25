package com.ihomy.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 电子图书表单:标题+文件URL必填,其余选填。
 */
@Data
public class LibraryDTO {
    @NotBlank(message = "书名不能为空")
    private String title;
    private String author;
    private String description;
    private String coverUrl;
    @NotBlank(message = "文件不能为空")
    private String fileUrl;
    private String fileFormat;
    private Long fileSize;
    private String category;
    private String tags;
    private Integer status;
    private Integer visibility;
}
