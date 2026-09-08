package com.ihomy.dto;

import lombok.Data;

import java.util.List;

/**
 * AI 图片生成请求(V9.40):OpenAI 兼容 /images/generations。
 * 参数对齐方舟图片生成 API(尺寸/张数/参考图/种子/引导强度/水印/响应格式/组图)。
 */
@Data
public class AiImageDTO {
    /** 图片描述 */
    private String prompt;
    /** 尺寸(如 2048x2048 / 2K / 4K / adaptive,可选,留空用服务端默认) */
    private String size;
    /** 张数(1~4,可选,默认 1;组图模式 auto 时不传) */
    private Integer n;
    /** 参考图(图生图,URL 或 base64 dataUrl,最多 10 张) */
    private List<String> imageUrls;
    /** 随机种子(0~2147483647,null 或 -1=随机不传) */
    private Long seed;
    /** 提示词引导强度(1~10,null=模型默认不传) */
    private Double guidanceScale;
    /** 水印(null=服务端默认开) */
    private Boolean watermark;
    /** 响应格式 url/b64_json(默认 url) */
    private String responseFormat;
    /** 组图模式:auto=自动组图(其余值不传) */
    private String sequentialMode;
    /** 组图最大张数(1~10,sequentialMode=auto 时生效) */
    private Integer sequentialMaxImages;
}
