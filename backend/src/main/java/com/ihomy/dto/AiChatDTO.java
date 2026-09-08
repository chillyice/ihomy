package com.ihomy.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * AI 对话测试请求:OpenAI 兼容 /chat/completions(多轮消息透传)。
 */
@Data
public class AiChatDTO {
    /** 多轮消息(role: system/user/assistant + content) */
    private List<Map<String, String>> messages;
    /** 采样温度(0~2,可选,缺省 0.2) */
    private Double temperature;
}
