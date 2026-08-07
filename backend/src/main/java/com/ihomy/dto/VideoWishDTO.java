package com.ihomy.dto;

import lombok.Data;

/**
 * "想看"表单:片名/题材标签/理由。
 */
@Data
public class VideoWishDTO {
    private String title;
    private String genres;
    private String reason;
}
