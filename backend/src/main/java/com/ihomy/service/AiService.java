package com.ihomy.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import com.ihomy.common.ThirdPartyHttp;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AI 模型统一接入层(V9.40):OpenAI 兼容协议(POST {base-url}/chat/completions),
 * 供物品定位 AI 语义及后续需要大模型的功能(聊天/内容生成)复用。
 * 配置走 app.ai.*(application.yml 留空,external.yml 覆盖;api-key 支持 ENC(...) 密文,
 * 解密与天气私钥同机制走 ParameterService);base-url/api-key/model 任一为空即未启用。
 * 三方出站一律走 ThirdPartyHttp(thirdparty 日志 + URL/头脱敏);失败转 BizException
 * 由全局异常处理兜底(底层 IOException 堆栈已在 thirdparty 日志落盘)。
 */
@Service
@RequiredArgsConstructor
public class AiService {

    /** OpenAI 兼容服务地址(不含路径,如 https://api.deepseek.com) */
    @Value("${app.ai.base-url:}")
    private String baseUrl;

    /** API Key,支持 ENC(...) AES-GCM 密文 */
    @Value("${app.ai.api-key:}")
    private String apiKey;

    /** 模型名(如 deepseek-chat) */
    @Value("${app.ai.model:}")
    private String model;

    /** 单次调用超时(毫秒),大模型响应较慢默认 30s */
    @Value("${app.ai.timeout-ms:30000}")
    private int timeoutMs;

    /** 图片生成模型名(如 doubao-seedream-4-0),留空=图片能力未启用 */
    @Value("${app.ai.image-model:}")
    private String imageModel;

    /** 图片生成服务地址,留空复用 base-url(走同一代理) */
    @Value("${app.ai.image-base-url:}")
    private String imageBaseUrl;

    /** 图片生成 API Key,留空复用 api-key */
    @Value("${app.ai.image-api-key:}")
    private String imageApiKey;

    /** 语音识别模型名(如 SenseVoice),留空=语音能力未启用 */
    @Value("${app.ai.asr-model:}")
    private String asrModel;

    /** 语音识别服务地址,留空复用 base-url */
    @Value("${app.ai.asr-base-url:}")
    private String asrBaseUrl;

    /** 语音识别 API Key,留空复用 api-key */
    @Value("${app.ai.asr-api-key:}")
    private String asrApiKey;

    private final ParameterService parameterService;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    /** AI 功能是否已配置启用(未配置时调用方应友好提示而非报 500) */
    public boolean isAvailable() {
        return notBlank(baseUrl) && notBlank(apiKey) && notBlank(model);
    }

    /** 各能力配置状态(Playground):能力→available/model,另附统一超时;模型名非敏感(仅模型标识) */
    public Map<String, Object> status() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("chat", capability(isAvailable(), model));
        out.put("image", capability(isImageAvailable(), imageModel));
        out.put("asr", capability(isAsrAvailable(), asrModel));
        out.put("timeoutMs", timeoutMs);
        return out;
    }

    private Map<String, Object> capability(boolean available, String model) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("available", available);
        m.put("model", model == null ? "" : model.trim());
        return m;
    }

    /** 图片生成是否已启用(模型名配齐即可;地址/Key 缺省复用主配置) */
    public boolean isImageAvailable() {
        return notBlank(imageModel) && notBlank(imageEndpointBase()) && notBlank(imageKey());
    }

    /** 语音识别是否已启用 */
    public boolean isAsrAvailable() {
        return notBlank(asrModel) && notBlank(asrEndpointBase()) && notBlank(asrKey());
    }

    private String imageEndpointBase() {
        return notBlank(imageBaseUrl) ? imageBaseUrl : baseUrl;
    }

    private String imageKey() {
        return notBlank(imageApiKey) ? imageApiKey : apiKey;
    }

    private String asrEndpointBase() {
        return notBlank(asrBaseUrl) ? asrBaseUrl : baseUrl;
    }

    private String asrKey() {
        return notBlank(asrApiKey) ? asrApiKey : apiKey;
    }

    /** 多轮消息对话,返回首条回复文本(messages 元素含 role 与 content,temperature 缺省 0.2) */
    public String chat(List<Map<String, String>> messages) {
        return chat(messages, null);
    }

    /** 多轮消息对话带自定义采样温度(Playground 调参用) */
    public String chat(List<Map<String, String>> messages, Double temperature) {
        return doChat(messages, false, temperature);
    }

    /** 单轮问答并解析 JSON 回复:JSON 模式 + markdown 围栏容错 */
    public JsonNode chatJson(String systemPrompt, String userContent) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(message("system", systemPrompt));
        messages.add(message("user", userContent));
        return parseJson(doChat(messages, true, null));
    }

    private String doChat(List<Map<String, String>> messages, boolean jsonMode, Double temperature) {
        if (!isAvailable()) {
            throw new BizException(ResultCode.BAD_REQUEST, "AI 服务未配置,请在 external.yml 配置 app.ai 后重启后端");
        }
        String url = stripTrailingSlash(baseUrl) + "/chat/completions";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", temperature == null ? 0.2 : temperature);
        if (jsonMode) {
            body.put("response_format", Map.of("type", "json_object"));
        }
        Map<String, String> headers = Map.of(
                "Content-Type", "application/json",
                "Authorization", "Bearer " + decryptIfEnc(apiKey));
        try {
            ThirdPartyHttp.Resp resp = ThirdPartyHttp.request("ai", "POST", url, headers,
                    mapper.writeValueAsBytes(body), timeoutMs);
            // 部分兼容端点不支持 response_format,400 且报文提到该字段时去掉重试一次
            if (resp.status() == 400 && jsonMode && resp.body() != null && resp.body().contains("response_format")) {
                body.remove("response_format");
                resp = ThirdPartyHttp.request("ai", "POST", url, headers,
                        mapper.writeValueAsBytes(body), timeoutMs);
            }
            if (!resp.ok()) {
                throw new BizException(ResultCode.INTERNAL_ERROR,
                        "AI 服务返回异常(" + resp.status() + "):" + truncate(resp.body(), 200));
            }
            JsonNode root = mapper.readTree(resp.body());
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (!content.isTextual() || content.asText().isBlank()) {
                throw new BizException(ResultCode.INTERNAL_ERROR, "AI 返回内容为空");
            }
            return content.asText();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "AI 服务调用失败:" + e.getMessage());
        }
    }

    /** 剥 markdown 围栏后解析 JSON(部分模型即使 JSON 模式也会包 ```json) */
    private JsonNode parseJson(String content) {
        String text = content.trim();
        if (text.startsWith("```")) {
            int first = text.indexOf('\n');
            int last = text.lastIndexOf("```");
            if (first > 0 && last > first) {
                text = text.substring(first + 1, last).trim();
            }
        }
        try {
            return mapper.readTree(text);
        } catch (Exception e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "AI 返回格式无法解析");
        }
    }

    /** OpenAI 兼容图片生成:POST {base}/images/generations,返回 data 数组(元素含 url 或 b64_json) */
    public List<Map<String, Object>> images(String prompt, String size, Integer n) {
        if (!isImageAvailable()) {
            throw new BizException(ResultCode.BAD_REQUEST, "AI 图片生成未配置,请在 external.yml 配置 app.ai.image-model 后重启后端");
        }
        requireText(prompt, "请填写图片描述");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", imageModel.trim());
        body.put("prompt", prompt.trim());
        body.put("n", n == null || n < 1 ? 1 : Math.min(n, 4));
        if (notBlank(size)) {
            body.put("size", size.trim());
        }
        body.put("response_format", "url");
        Map<String, String> headers = Map.of(
                "Content-Type", "application/json",
                "Authorization", "Bearer " + decryptIfEnc(imageKey()));
        try {
            String url = stripTrailingSlash(imageEndpointBase()) + "/images/generations";
            ThirdPartyHttp.Resp resp = ThirdPartyHttp.request("ai", "POST", url, headers,
                    mapper.writeValueAsBytes(body), timeoutMs);
            // 个别端点不支持 response_format,400 且报文提到该字段时去掉重试一次
            if (resp.status() == 400 && resp.body() != null && resp.body().contains("response_format")) {
                body.remove("response_format");
                resp = ThirdPartyHttp.request("ai", "POST", url, headers,
                        mapper.writeValueAsBytes(body), timeoutMs);
            }
            if (!resp.ok()) {
                throw new BizException(ResultCode.INTERNAL_ERROR,
                        "AI 图片生成返回异常(" + resp.status() + "):" + truncate(resp.body(), 200));
            }
            JsonNode data = mapper.readTree(resp.body()).path("data");
            if (!data.isArray() || data.isEmpty()) {
                throw new BizException(ResultCode.INTERNAL_ERROR, "AI 图片生成返回格式异常");
            }
            List<Map<String, Object>> out = new ArrayList<>();
            for (JsonNode item : data) {
                out.add(mapper.convertValue(item, MAP_TYPE));
            }
            return out;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "AI 图片生成调用失败:" + e.getMessage());
        }
    }

    /** OpenAI 兼容语音识别:multipart POST {base}/audio/transcriptions,返回转写文本 */
    public Map<String, Object> transcribe(byte[] audio, String filename, String mimeType, String language) {
        if (!isAsrAvailable()) {
            throw new BizException(ResultCode.BAD_REQUEST, "AI 语音识别未配置,请在 external.yml 配置 app.ai.asr-model 后重启后端");
        }
        if (audio == null || audio.length == 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "请上传音频文件");
        }
        String boundary = "ihomy-ai-" + UUID.randomUUID().toString().replace("-", "");
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("model", asrModel.trim());
        if (notBlank(language)) {
            fields.put("language", language.trim());
        }
        Map<String, String> headers = Map.of(
                "Content-Type", "multipart/form-data; boundary=" + boundary,
                "Authorization", "Bearer " + decryptIfEnc(asrKey()));
        try {
            byte[] body = multipart(boundary, fields, "file",
                    filename == null || filename.isBlank() ? "audio.wav" : filename, mimeType, audio);
            String url = stripTrailingSlash(asrEndpointBase()) + "/audio/transcriptions";
            ThirdPartyHttp.Resp resp = ThirdPartyHttp.request("ai", "POST", url, headers, body, timeoutMs);
            if (!resp.ok()) {
                throw new BizException(ResultCode.INTERNAL_ERROR,
                        "AI 语音识别返回异常(" + resp.status() + "):" + truncate(resp.body(), 200));
            }
            String text = mapper.readTree(resp.body()).path("text").asText("");
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("text", text);
            return out;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "AI 语音识别调用失败:" + e.getMessage());
        }
    }

    /** 手工构建 multipart/form-data 请求体(字段 + 单文件;ThirdPartyHttp 只收 byte[] body) */
    private byte[] multipart(String boundary, Map<String, String> fields,
                             String fileField, String filename, String mimeType, byte[] file) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String prefix = "--" + boundary + "\r\n";
        try {
            for (Map.Entry<String, String> e : fields.entrySet()) {
                out.write((prefix + "Content-Disposition: form-data; name=\"" + e.getKey()
                        + "\"\r\n\r\n" + e.getValue() + "\r\n").getBytes(StandardCharsets.UTF_8));
            }
            out.write((prefix + "Content-Disposition: form-data; name=\"" + fileField
                    + "\"; filename=\"" + filename + "\"\r\n"
                    + "Content-Type: " + (mimeType == null || mimeType.isBlank()
                    ? "application/octet-stream" : mimeType) + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(file);
            out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return out.toByteArray();
        } catch (IOException e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "语音识别请求构建失败:" + e.getMessage());
        }
    }

    /** 密钥支持 ENC(...) 密文(盐值 sys_parameter.aes-salt) */
    private String decryptIfEnc(String value) {
        String v = value == null ? "" : value.trim();
        if (v.startsWith("ENC(") && v.endsWith(")")) {
            return parameterService.decrypt(v);
        }
        return v;
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    private void requireText(String s, String msg) {
        if (s == null || s.isBlank()) throw new BizException(ResultCode.BAD_REQUEST, msg);
    }

    private String stripTrailingSlash(String s) {
        String t = s.trim();
        return t.endsWith("/") ? t.substring(0, t.length() - 1) : t;
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
