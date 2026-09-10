package com.ihomy.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ihomy.common.AiConst;
import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import com.ihomy.common.ThirdPartyHttp;
import com.ihomy.dto.AiImageDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * AI 模型统一接入层(V9.40,按功能解析模型 V9.48):OpenAI 兼容协议(POST {base-url}/chat/completions),
 * 供物品定位 AI 语义及后续需要大模型的功能(聊天/内容生成)复用。
 * 每个功能按其 feature_code 从家庭模型池解析模型(FamilyAiConfigService.resolveForFeature),
 * 无全局兜底;base-url/api-key/model 任一为空即该功能未配置。三方出站一律走 ThirdPartyHttp
 * (thirdparty 日志 + URL/头脱敏);失败转 BizException 由全局异常处理兜底。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private final FamilyAiConfigService familyAiConfigService;
    private final ParameterService parameterService;
    private final BaiduAsrClient baiduAsrClient;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    /** 各能力配置状态(Playground):能力→available/model,另附统一超时;模型名非敏感(仅模型标识) */
    public Map<String, Object> status(Long familyId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("chat", capability(familyId, AiConst.FEATURE_CHAT));
        out.put("image", capability(familyId, AiConst.FEATURE_IMAGE));
        out.put("weatherImage", capability(familyId, AiConst.FEATURE_WEATHER_IMAGE));
        out.put("asr", capability(familyId, AiConst.FEATURE_ASR));
        return out;
    }

    private Map<String, Object> capability(Long familyId, String featureCode) {
        FamilyAiConfigService.AiConfig c = familyAiConfigService.resolveForFeature(familyId, featureCode);
        boolean available = familyAiConfigService.isAvailable(c);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("available", available);
        m.put("model", c.model() == null ? "" : c.model().trim());
        m.put("timeoutMs", c.timeoutMs());
        return m;
    }

    /** 多轮消息对话,返回首条回复文本(messages 元素含 role 与 content,temperature 缺省 0.2) */
    public String chat(Long familyId, List<Map<String, String>> messages) {
        return chat(familyId, messages, null);
    }

    /** 多轮消息对话带自定义采样温度(Playground 调参用);主模型报错时回退兜底模型(容灾) */
    public String chat(Long familyId, List<Map<String, String>> messages, Double temperature) {
        FamilyAiConfigService.Chain chain = familyAiConfigService.resolveChain(familyId, AiConst.FEATURE_CHAT);
        try {
            return doChat(chain.primary(), messages, false, temperature);
        } catch (RuntimeException e) {
            if (chain.hasFallback()) {
                log.warn("[AI对话] 主模型失败,回退兜底模型 err={}", e.getMessage());
                return doChat(chain.fallback(), messages, false, temperature);
            }
            throw e;
        }
    }

    /** 单轮问答并解析 JSON 回复:JSON 模式 + markdown 围栏容错;featureCode 指定用哪个功能绑定的模型 */
    public JsonNode chatJson(Long familyId, String featureCode, String systemPrompt, String userContent) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(message("system", systemPrompt));
        messages.add(message("user", userContent));
        return parseJson(doChat(familyAiConfigService.resolveForFeature(familyId, featureCode), messages, true, null));
    }

    /** 单轮问答并解析 JSON 回复:显式指定模型配置(找物/放物用兜底模型时传 fallback 配置) */
    public JsonNode chatJson(FamilyAiConfigService.AiConfig c, String systemPrompt, String userContent) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(message("system", systemPrompt));
        messages.add(message("user", userContent));
        return parseJson(doChat(c, messages, true, null));
    }

    private String doChat(FamilyAiConfigService.AiConfig c, List<Map<String, String>> messages,
                          boolean jsonMode, Double temperature) {
        if (c == null || !notBlank(c.baseUrl()) || !notBlank(c.apiKey()) || !notBlank(c.model())) {
            throw new BizException(ResultCode.BAD_REQUEST, "AI 服务未配置,请家长在设置-家庭 AI 配置中填写");
        }
        String url = stripTrailingSlash(c.baseUrl()) + "/chat/completions";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", c.model().trim());
        body.put("messages", messages);
        body.put("temperature", temperature == null ? 0.2 : temperature);
        if (jsonMode) {
            body.put("response_format", Map.of("type", "json_object"));
        }
        Map<String, String> headers = Map.of(
                "Content-Type", "application/json",
                "Authorization", "Bearer " + decryptIfEnc(c.apiKey()));
        try {
            ThirdPartyHttp.Resp resp = ThirdPartyHttp.request("ai", "POST", url, headers,
                    mapper.writeValueAsBytes(body), c.timeoutMs());
            // 部分兼容端点不支持 response_format,400 且报文提到该字段时去掉重试一次
            if (resp.status() == 400 && jsonMode && resp.body() != null && resp.body().contains("response_format")) {
                body.remove("response_format");
                resp = ThirdPartyHttp.request("ai", "POST", url, headers,
                        mapper.writeValueAsBytes(body), c.timeoutMs());
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

    /** OpenAI 兼容图片生成:POST {base}/images/generations,返回 data 数组(元素含 url 或 b64_json);主报错回退兜底 */
    public List<Map<String, Object>> images(Long familyId, AiImageDTO dto) {
        return images(familyId, AiConst.FEATURE_IMAGE, dto);
    }

    /** OpenAI 兼容图片生成:指定 featureCode(如 WEATHER_IMAGE 天气生图),按该功能绑定的模型解析 */
    public List<Map<String, Object>> images(Long familyId, String featureCode, AiImageDTO dto) {
        FamilyAiConfigService.Chain chain = familyAiConfigService.resolveChain(familyId, featureCode);
        try {
            return doImages(chain.primary(), dto);
        } catch (RuntimeException e) {
            if (chain.hasFallback()) {
                log.warn("[AI图片] 主模型失败,回退兜底模型 err={}", e.getMessage());
                return doImages(chain.fallback(), dto);
            }
            throw e;
        }
    }

    private List<Map<String, Object>> doImages(FamilyAiConfigService.AiConfig c, AiImageDTO dto) {
        if (c == null || !notBlank(c.baseUrl()) || !notBlank(c.apiKey()) || !notBlank(c.model())) {
            throw new BizException(ResultCode.BAD_REQUEST, "AI 图片生成未配置,请家长在设置-家庭 AI 配置中填写");
        }
        requireText(dto.getPrompt(), "请填写图片描述");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", c.model().trim());
        body.put("prompt", dto.getPrompt().trim());
        // Seedream 5.0 pro 不支持组图(sequential_image_generation)且仅出单图;强制单图,
        // 不传组图字段、n 恒为 1——避免把 lite 的组图/多图规则套到 pro 上被方舟拒绝
        boolean pro = detectImageEngine(c) == ImageEngine.SEEDREAM_PRO;
        // 组图模式由 sequential_image_generation 驱动,此时不再传 n(避免两者语义冲突)
        boolean groupMode = !pro && "auto".equals(dto.getSequentialMode());
        if (groupMode) {
            body.put("sequential_image_generation", "auto");
            Map<String, Object> seqOpts = new LinkedHashMap<>();
            int maxImages = dto.getSequentialMaxImages() == null ? 4 : dto.getSequentialMaxImages();
            seqOpts.put("max_images", Math.min(Math.max(maxImages, 1), 10));
            body.put("sequential_image_generation_options", seqOpts);
        } else {
            int n;
            if (pro) {
                n = 1;
            } else {
                n = dto.getN() == null || dto.getN() < 1 ? 1 : Math.min(dto.getN(), 4);
            }
            body.put("n", n);
        }
        if (dto.getImageUrls() != null && !dto.getImageUrls().isEmpty()) {
            List<String> refs = dto.getImageUrls().stream()
                    .filter(s -> s != null && !s.isBlank())
                    .limit(10)
                    .toList();
            if (!refs.isEmpty()) {
                body.put("image", refs.size() == 1 ? refs.get(0) : refs);
            }
        }
        if (notBlank(dto.getSize())) {
            body.put("size", dto.getSize().trim());
        }
        // 可选参数:仅在前端显式给出时透传(null=用模型默认,不进请求体)
        if (dto.getSeed() != null && dto.getSeed() >= 0) {
            body.put("seed", Math.min(dto.getSeed(), 2147483647L));
        }
        if (dto.getGuidanceScale() != null) {
            body.put("guidance_scale", Math.min(Math.max(dto.getGuidanceScale(), 1.0), 10.0));
        }
        if (dto.getWatermark() != null) {
            body.put("watermark", dto.getWatermark());
        }
        body.put("response_format", "b64_json".equals(dto.getResponseFormat()) ? "b64_json" : "url");
        Map<String, String> headers = Map.of(
                "Content-Type", "application/json",
                "Authorization", "Bearer " + decryptIfEnc(c.apiKey()));
        try {
            String url = stripTrailingSlash(c.baseUrl()) + "/images/generations";
            ThirdPartyHttp.Resp resp = ThirdPartyHttp.request("ai", "POST", url, headers,
                    mapper.writeValueAsBytes(body), c.timeoutMs());
            // 个别端点不支持 response_format,400 且报文提到该字段时去掉重试一次
            if (resp.status() == 400 && resp.body() != null && resp.body().contains("response_format")) {
                body.remove("response_format");
                resp = ThirdPartyHttp.request("ai", "POST", url, headers,
                        mapper.writeValueAsBytes(body), c.timeoutMs());
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

    /** 图片模型引擎:按 model 名归一化区分 Seedream 5.0 pro(仅单图、size 规则不同)与其它(组图兼容) */
    private enum ImageEngine { GENERIC, SEEDREAM, SEEDREAM_PRO }

    /** 识别绑定的图片模型属于哪个引擎:pro 走单图+pro size 规则,其余保持组图+通用规则 */
    private ImageEngine detectImageEngine(FamilyAiConfigService.AiConfig c) {
        if (c == null || !notBlank(c.model())) return ImageEngine.GENERIC;
        String m = c.model().trim().toLowerCase();
        boolean seedream = m.contains("seedream");
        if (seedream && m.contains("pro")) return ImageEngine.SEEDREAM_PRO;
        if (seedream) return ImageEngine.SEEDREAM;
        return ImageEngine.GENERIC;
    }

    /** 语音识别:OpenAI 兼容 multipart(/audio/transcriptions)或百度短语音(provider=BAIDU);主报错/空结果回退兜底 */
    public Map<String, Object> transcribe(Long familyId, byte[] audio, String filename, String mimeType,
                                          String language, Integer rate) {
        FamilyAiConfigService.Chain chain = familyAiConfigService.resolveChain(familyId, AiConst.FEATURE_ASR);
        try {
            Map<String, Object> out = doTranscribe(chain.primary(), familyId, audio, filename, mimeType, language, rate);
            if (out != null && !String.valueOf(out.getOrDefault("text", "")).isBlank()) return out;
            if (chain.hasFallback()) {
                log.warn("[AI语音] 主模型空结果,回退兜底模型");
                return doTranscribe(chain.fallback(), familyId, audio, filename, mimeType, language, rate);
            }
            return out;
        } catch (RuntimeException e) {
            if (chain.hasFallback()) {
                log.warn("[AI语音] 主模型失败,回退兜底模型 err={}", e.getMessage());
                return doTranscribe(chain.fallback(), familyId, audio, filename, mimeType, language, rate);
            }
            throw e;
        }
    }

    private Map<String, Object> doTranscribe(FamilyAiConfigService.AiConfig c, Long familyId, byte[] audio,
                                             String filename, String mimeType, String language, Integer rate) {
        if (c != null && AiConst.PROVIDER_BAIDU.equals(c.provider())) {
            return transcribeBaidu(c, familyId, audio, filename, rate);
        }
        if (c == null || !notBlank(c.baseUrl()) || !notBlank(c.apiKey()) || !notBlank(c.model())) {
            throw new BizException(ResultCode.BAD_REQUEST, "AI 语音识别未配置,请家长在设置-家庭 AI 配置中填写");
        }
        if (audio == null || audio.length == 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "请上传音频文件");
        }
        String boundary = "ihomy-ai-" + UUID.randomUUID().toString().replace("-", "");
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("model", c.model().trim());
        if (notBlank(language)) {
            fields.put("language", language.trim());
        }
        Map<String, String> headers = Map.of(
                "Content-Type", "multipart/form-data; boundary=" + boundary,
                "Authorization", "Bearer " + decryptIfEnc(c.apiKey()));
        try {
            byte[] body = multipart(boundary, fields, "file",
                    filename == null || filename.isBlank() ? "audio.wav" : filename, mimeType, audio);
            String url = stripTrailingSlash(c.baseUrl()) + "/audio/transcriptions";
            ThirdPartyHttp.Resp resp = ThirdPartyHttp.request("ai", "POST", url, headers, body, c.timeoutMs());
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

    /** 百度短语音识别:access_token 由 BaiduAsrClient 缓存换取;format 按扩展名推断,rate 缺省 16000 */
    private Map<String, Object> transcribeBaidu(FamilyAiConfigService.AiConfig c, Long familyId,
                                                byte[] audio, String filename, Integer rate) {
        if (!notBlank(c.baseUrl()) || !notBlank(c.apiKey()) || !notBlank(c.model())) {
            throw new BizException(ResultCode.BAD_REQUEST, "百度语音识别未配置,请家长在设置-家庭 AI 配置中填写");
        }
        if (!notBlank(c.secretKey())) {
            throw new BizException(ResultCode.BAD_REQUEST, "百度语音识别缺 Secret Key,请在模型配置中填写");
        }
        if (audio == null || audio.length == 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "请上传音频文件");
        }
        int r = rate == null ? 16000 : rate;
        if (r != 16000 && r != 8000) {
            throw new BizException(ResultCode.BAD_REQUEST, "采样率仅支持 16000/8000");
        }
        String text = baiduAsrClient.recognize(
                stripTrailingSlash(c.baseUrl()),
                decryptIfEnc(c.apiKey()),
                decryptIfEnc(c.secretKey()),
                c.model(),
                audioFormat(filename),
                r,
                "ihomy-family-" + familyId,
                audio,
                c.timeoutMs());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("text", text);
        return out;
    }

    /** 按文件扩展名推断百度音频 format:仅 wav/amr/m4a 显式,其余按 pcm */
    private String audioFormat(String filename) {
        if (filename == null) return "pcm";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".wav")) return "wav";
        if (lower.endsWith(".amr")) return "amr";
        if (lower.endsWith(".m4a")) return "m4a";
        return "pcm";
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
