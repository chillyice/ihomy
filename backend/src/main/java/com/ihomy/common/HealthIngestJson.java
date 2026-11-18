package com.ihomy.common;

import com.ihomy.dto.HealthIngestDTO;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * 上报请求体的宽容解析:快捷指令把「请求体」设成 JSON 时,粘贴整段 JSON 会变成
 * {"": "<整段 JSON>"}(键留空、整段当值),这里先拆一层再反序列化。
 * 请求体来源不设限(text/plain、无 Content-Type 都能送),解析失败一律给 400 的用户可读提示。
 */
public final class HealthIngestJson {

    private HealthIngestJson() {
    }

    public static HealthIngestDTO parse(ObjectMapper om, String raw) {
        String text = unwrap(om, raw);
        if (text.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "请求体是空的,请把帮助页里那段 JSON 放进请求体");
        }
        try {
            HealthIngestDTO dto = om.readValue(text, HealthIngestDTO.class);
            if (dto != null) {
                return dto;
            }
        } catch (JacksonException e) {
            // 落到下面统一提示
        }
        throw new BizException(ResultCode.BAD_REQUEST, "请求体不是合法的 JSON,请照帮助页里的示例原样发送");
    }

    /** 单键、键为空串而值是 JSON 文本(或键是 JSON 文本、值为空)时拆出内层 JSON,其余原样返回 */
    static String unwrap(ObjectMapper om, String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim();
        if (!text.startsWith("{") || text.length() < 4) {
            return text;
        }
        JsonNode node;
        try {
            node = om.readTree(text);
        } catch (JacksonException e) {
            return text;
        }
        if (node == null || !node.isObject() || node.size() != 1) {
            return text;
        }
        Map.Entry<String, JsonNode> entry = node.properties().iterator().next();
        String key = entry.getKey() == null ? "" : entry.getKey().trim();
        JsonNode value = entry.getValue();
        if (key.isEmpty() && isJsonText(value)) {
            return value.asString().trim();
        }
        if (looksLikeJson(key) && (value == null || value.isNull() || (value.isString() && value.asString().isBlank()))) {
            return key;
        }
        return text;
    }

    private static boolean isJsonText(JsonNode node) {
        return node != null && node.isString() && looksLikeJson(node.asString());
    }

    private static boolean looksLikeJson(String s) {
        String t = s == null ? "" : s.trim();
        return t.startsWith("{") && t.endsWith("}");
    }
}
