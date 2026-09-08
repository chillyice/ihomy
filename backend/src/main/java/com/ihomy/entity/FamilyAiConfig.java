package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 家庭级 AI API 配置实体(sys_family_ai_config):每家庭一行,按家庭独立配置 AI 能力。
 * 各字段留空=跟随全局 app.ai.*(application.yml/external.yml)兜底;
 * api_key/image_api_key/asr_api_key ENC 加密存储(AES-GCM,盐值 sys_parameter.aes-salt),不回传前端。
 */
@Data
@TableName("sys_family_ai_config")
public class FamilyAiConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    /** OpenAI 兼容服务地址(不含路径),留空跟随全局 */
    private String baseUrl;
    /** API Key(ENC 加密存储),留空跟随全局 */
    private String apiKey;
    /** 对话模型名,留空跟随全局 */
    private String model;
    /** 单次调用超时(毫秒),留空跟随全局 */
    private Integer timeoutMs;
    /** 图片生成模型名,留空跟随全局 */
    private String imageModel;
    /** 图片生成服务地址,留空复用 baseUrl(家庭内)再落全局 */
    private String imageBaseUrl;
    /** 图片生成 API Key(ENC 加密存储),留空复用 apiKey(家庭内)再落全局 */
    private String imageApiKey;
    /** 语音识别模型名,留空跟随全局 */
    private String asrModel;
    /** 语音识别服务地址,留空复用 baseUrl(家庭内)再落全局 */
    private String asrBaseUrl;
    /** 语音识别 API Key(ENC 加密存储),留空复用 apiKey(家庭内)再落全局 */
    private String asrApiKey;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
