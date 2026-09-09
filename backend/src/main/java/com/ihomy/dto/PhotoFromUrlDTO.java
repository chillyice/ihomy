package com.ihomy.dto;

import lombok.Data;

/**
 * 保存网络/Base64 图片到相册(AI 生图落库):url 为待保存的图片来源(data: 或 http(s))。
 */
@Data
public class PhotoFromUrlDTO {
    private String url;
}
