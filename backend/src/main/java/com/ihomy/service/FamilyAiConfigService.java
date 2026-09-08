package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import com.ihomy.entity.FamilyAiConfig;
import com.ihomy.mapper.FamilyAiConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 家庭级 AI API 配置(V9.43):sys_family_ai_config 每家庭一行,与全局 app.ai.* 双层配置。
 * 解析优先级:家庭行非空字段 > 全局 yml 兜底(application.yml/external.yml);
 * 家庭内 image/asr 地址与 Key 缺省复用家庭主配置(base_url/api_key),再落全局。
 * 写接口(Settings-家庭 AI 配置,family:manage):保存时密钥 ENC 加密入库、留空保留原值;
 * 删除家庭配置即回退全局兜底。真实 API Key 不入 git,经界面保存或各环境手动 INSERT。
 */
@Service
@RequiredArgsConstructor
public class FamilyAiConfigService {

    /** 全局兜底:OpenAI 兼容服务地址(不含路径) */
    @Value("${app.ai.base-url:}")
    private String globalBaseUrl;

    /** 全局兜底:API Key,支持 ENC(...) AES-GCM 密文 */
    @Value("${app.ai.api-key:}")
    private String globalApiKey;

    /** 全局兜底:对话模型名 */
    @Value("${app.ai.model:}")
    private String globalModel;

    /** 全局兜底:单次调用超时(毫秒) */
    @Value("${app.ai.timeout-ms:30000}")
    private Integer globalTimeoutMs;

    /** 全局兜底:图片生成模型名 */
    @Value("${app.ai.image-model:}")
    private String globalImageModel;

    /** 全局兜底:图片生成服务地址 */
    @Value("${app.ai.image-base-url:}")
    private String globalImageBaseUrl;

    /** 全局兜底:图片生成 API Key */
    @Value("${app.ai.image-api-key:}")
    private String globalImageApiKey;

    /** 全局兜底:语音识别模型名 */
    @Value("${app.ai.asr-model:}")
    private String globalAsrModel;

    /** 全局兜底:语音识别服务地址 */
    @Value("${app.ai.asr-base-url:}")
    private String globalAsrBaseUrl;

    /** 全局兜底:语音识别 API Key */
    @Value("${app.ai.asr-api-key:}")
    private String globalAsrApiKey;

    private final FamilyAiConfigMapper familyAiConfigMapper;
    private final ParameterService parameterService;

    /** 解析后的生效配置:家庭行合并全局兜底后的完整快照(apiKey 为原文/ENC 原样,解密延迟到出站前) */
    public record AiConfig(String baseUrl, String apiKey, String model, Integer timeoutMs,
                           String imageModel, String imageBaseUrl, String imageApiKey,
                           String asrModel, String asrBaseUrl, String asrApiKey) {
    }

    /** 按家庭解析生效配置:家庭行非空字段覆盖全局兜底;familyId 为空仅全局 */
    public AiConfig resolve(Long familyId) {
        FamilyAiConfig row = familyId == null ? null : familyAiConfigMapper.selectOne(
                new LambdaQueryWrapper<FamilyAiConfig>().eq(FamilyAiConfig::getFamilyId, familyId));
        return new AiConfig(
                firstNonBlank(row == null ? null : row.getBaseUrl(), globalBaseUrl),
                firstNonBlank(row == null ? null : row.getApiKey(), globalApiKey),
                firstNonBlank(row == null ? null : row.getModel(), globalModel),
                row != null && row.getTimeoutMs() != null ? row.getTimeoutMs() : globalTimeoutMs,
                firstNonBlank(row == null ? null : row.getImageModel(), globalImageModel),
                firstNonBlank(row == null ? null : row.getImageBaseUrl(), globalImageBaseUrl),
                firstNonBlank(row == null ? null : row.getImageApiKey(), globalImageApiKey),
                firstNonBlank(row == null ? null : row.getAsrModel(), globalAsrModel),
                firstNonBlank(row == null ? null : row.getAsrBaseUrl(), globalAsrBaseUrl),
                firstNonBlank(row == null ? null : row.getAsrApiKey(), globalAsrApiKey));
    }

    /** 家庭配置详情(Settings 展示):自家字段(密钥只回 *Set 布尔)+ 全局兜底值 + 生效可用状态 */
    public Map<String, Object> get(Long familyId) {
        FamilyAiConfig row = familyAiConfigMapper.selectOne(
                new LambdaQueryWrapper<FamilyAiConfig>().eq(FamilyAiConfig::getFamilyId, familyId));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("configured", row != null);
        if (row != null) {
            m.put("baseUrl", nullToEmpty(row.getBaseUrl()));
            m.put("model", nullToEmpty(row.getModel()));
            m.put("timeoutMs", row.getTimeoutMs());
            m.put("apiKeySet", notBlank(row.getApiKey()));
            m.put("imageModel", nullToEmpty(row.getImageModel()));
            m.put("imageBaseUrl", nullToEmpty(row.getImageBaseUrl()));
            m.put("imageApiKeySet", notBlank(row.getImageApiKey()));
            m.put("asrModel", nullToEmpty(row.getAsrModel()));
            m.put("asrBaseUrl", nullToEmpty(row.getAsrBaseUrl()));
            m.put("asrApiKeySet", notBlank(row.getAsrApiKey()));
            m.put("remark", nullToEmpty(row.getRemark()));
            m.put("updatedAt", row.getUpdatedAt());
        }
        Map<String, Object> glob = new LinkedHashMap<>();
        glob.put("baseUrl", nullToEmpty(globalBaseUrl));
        glob.put("model", nullToEmpty(globalModel));
        glob.put("timeoutMs", globalTimeoutMs);
        glob.put("apiKeySet", notBlank(globalApiKey));
        glob.put("imageModel", nullToEmpty(globalImageModel));
        glob.put("asrModel", nullToEmpty(globalAsrModel));
        m.put("global", glob);
        AiConfig eff = resolve(familyId);
        m.put("available", Map.of(
                "chat", notBlank(eff.baseUrl()) && notBlank(eff.apiKey()) && notBlank(eff.model()),
                "image", notBlank(eff.imageModel()) && notBlank(imageBase(eff)) && notBlank(imageKey(eff)),
                "asr", notBlank(eff.asrModel()) && notBlank(asrBase(eff)) && notBlank(asrKey(eff))));
        return m;
    }

    /** 保存家庭配置(新增或更新):密钥 ENC 加密入库、留空保留原值;其余字段留空=跟随全局(存 NULL) */
    public void save(Long familyId, Map<String, String> body) {
        if (body == null) throw new BizException(ResultCode.BAD_REQUEST, "请求体不能为空");
        FamilyAiConfig row = familyAiConfigMapper.selectOne(
                new LambdaQueryWrapper<FamilyAiConfig>().eq(FamilyAiConfig::getFamilyId, familyId));
        boolean isNew = row == null;
        if (isNew) {
            row = new FamilyAiConfig();
            row.setFamilyId(familyId);
        }
        applyField(row::setBaseUrl, body.get("baseUrl"));
        applyField(row::setModel, body.get("model"));
        applyField(row::setImageModel, body.get("imageModel"));
        applyField(row::setImageBaseUrl, body.get("imageBaseUrl"));
        applyField(row::setAsrModel, body.get("asrModel"));
        applyField(row::setAsrBaseUrl, body.get("asrBaseUrl"));
        applyField(row::setRemark, body.get("remark"));
        if (body.get("timeoutMs") != null) {
            String t = body.get("timeoutMs").trim();
            if (t.isEmpty()) {
                row.setTimeoutMs(null);
            } else {
                try {
                    int v = Integer.parseInt(t);
                    row.setTimeoutMs(Math.min(Math.max(v, 1000), 600000));
                } catch (NumberFormatException e) {
                    throw new BizException(ResultCode.BAD_REQUEST, "超时时间须为毫秒数字");
                }
            }
        }
        applySecret(row::setApiKey, body.get("apiKey"), row.getApiKey());
        applySecret(row::setImageApiKey, body.get("imageApiKey"), row.getImageApiKey());
        applySecret(row::setAsrApiKey, body.get("asrApiKey"), row.getAsrApiKey());
        if (isNew) {
            familyAiConfigMapper.insert(row);
        } else {
            familyAiConfigMapper.updateById(row);
        }
    }

    /** 删除家庭配置:回到全局兜底 */
    public void delete(Long familyId) {
        familyAiConfigMapper.delete(new LambdaQueryWrapper<FamilyAiConfig>()
                .eq(FamilyAiConfig::getFamilyId, familyId));
    }

    /** 文本字段保存:trim 后非空入库,空串置 NULL(=跟随全局);密钥字段走 applySecret */
    private void applyField(java.util.function.Consumer<String> setter, String value) {
        String v = value == null ? null : value.trim();
        setter.accept(v == null || v.isEmpty() ? null : v);
    }

    /** 密钥保存:留空(null/空串)保留原值,显式给出则 ENC 加密入库(已是 ENC(...) 密文不重复加密) */
    private void applySecret(java.util.function.Consumer<String> setter, String value, String existing) {
        if (value == null || value.isBlank()) return;
        String v = value.trim();
        if (v.startsWith("ENC(") && v.endsWith(")")) {
            setter.accept(v);
        } else {
            setter.accept(parameterService.encrypt(v));
        }
    }

    // ---------- 生效口径的复用判定(AiService 同款:地址/Key 缺省复用主配置) ----------

    String imageBase(AiConfig c) {
        return firstNonBlank(c.imageBaseUrl(), c.baseUrl());
    }

    String imageKey(AiConfig c) {
        return firstNonBlank(c.imageApiKey(), c.apiKey());
    }

    String asrBase(AiConfig c) {
        return firstNonBlank(c.asrBaseUrl(), c.baseUrl());
    }

    String asrKey(AiConfig c) {
        return firstNonBlank(c.asrApiKey(), c.apiKey());
    }

    private String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
