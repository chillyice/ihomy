package com.ihomy.dto;

import lombok.Data;

/**
 * 保存网络/Base64 图片到相册(AI 生图落库):url 为待保存的图片来源(data: 或 http(s))。
 * name/description 可选:天气生图传入图片名称(如「城市-日期-上下午-时间」)与提示词备注,留空沿用默认。
 */
@Data
public class PhotoFromUrlDTO {
    private String url;
    /** 图片名称(不含扩展名,留空默认 ai) */
    private String name;
    /** 图片备注(如生图 prompt,留空默认「AI 生图」) */
    private String description;
}
