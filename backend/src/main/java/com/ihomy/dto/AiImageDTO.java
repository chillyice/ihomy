package com.ihomy.dto;

import lombok.Data;

/**
 * AI 图片生成请求(V9.40):OpenAI 兼容 /images/generations。
 */
@Data
public class AiImageDTO {
    /** 图片描述 */
    private String prompt;
    /** 尺寸(如 1024x1024,可选,留空用服务端默认) */
    private String size;
    /** 张数(1~4,可选,默认 1) */
    private Integer n;
}
