package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 家庭级 AI 模型池实体(sys_family_ai_model):每家庭多条,type=LLM/IMAGE/ASR。
 * 每条自带 base_url/api_key/model,api_key ENC 加密存储不回传前端。
 */
@Data
@TableName("sys_family_ai_model")
public class AiModel {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    /** 显示名(如 tshl GLM 快模型) */
    private String name;
    /** 模型类型 LLM/IMAGE/ASR */
    private String type;
    /** OpenAI 兼容服务地址(不含路径) */
    private String baseUrl;
    /** API Key(ENC 加密存储) */
    private String apiKey;
    /** 真实模型标识(如 GLM-5.3-Flash / doubao-seedream-5-0-260128 / SenseVoice) */
    private String model;
    /** 单次调用超时(毫秒),留空=默认 30000 */
    private Integer timeoutMs;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
