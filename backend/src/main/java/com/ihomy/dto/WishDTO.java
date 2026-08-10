package com.ihomy.dto;

import lombok.Data;

/**
 * 愿望单表单:status 0待实现 1已实现 2放弃。
 */
@Data
public class WishDTO {
    private String title;
    private String reason;
    private String category;
    private Integer status;
}