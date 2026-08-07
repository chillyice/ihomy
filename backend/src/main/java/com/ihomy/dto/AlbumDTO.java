package com.ihomy.dto;

import lombok.Data;

/**
 * 相册表单:type public/private(缺省 public)。
 */
@Data
public class AlbumDTO {
    private String name;
    private String type;
}