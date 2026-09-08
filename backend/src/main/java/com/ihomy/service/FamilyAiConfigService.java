package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ihomy.common.AiConst;
import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import com.ihomy.entity.AiFeature;
import com.ihomy.entity.AiModel;
import com.ihomy.mapper.AiFeatureMapper;
import com.ihomy.mapper.AiModelMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 家庭级 AI 配置(V9.48 模型池+功能绑定;V9.49 加 LOCAL 本地规则):sys_family_ai_model 每家庭多条,
 * 类型 LLM/IMAGE/ASR/LOCAL;sys_family_ai_feature 每家庭每功能一行,绑定一个模型(model_id 可空=该功能停用)。
 * 每家庭自动拥有一个内置 LOCAL 模型(name=本地规则解析(离线)),不可删改;找物/放物可绑 LOCAL(本地优先)或 LLM(本地+LLM 兜底)。
 * 功能只能绑对应允许类型集合(AiConst.FEATURE_ALLOWED_TYPES 校验);api_key ENC 加密入库、不回传前端。
 */
@Service
@RequiredArgsConstructor
public class FamilyAiConfigService {

    /** 模型行 timeout 缺省值(毫秒) */
    private static final Integer DEFAULT_TIMEOUT_MS = 30000;
    /** 内置 LOCAL 模型的 model 占位(不参与出站调用) */
    private static final String LOCAL_MODEL = "local-rule";

    private final AiModelMapper aiModelMapper;
    private final AiFeatureMapper aiFeatureMapper;
    private final ParameterService parameterService;

    /** 解析后的生效模型快照(apiKey/secretKey 为原文/ENC 原样,解密延迟到出站前;type=LOCAL 时 base/apiKey/model 为空) */
    public record AiConfig(String baseUrl, String apiKey, String model, Integer timeoutMs, String type,
                           String provider, String secretKey) {
    }

    // ---------- 功能解析 ----------

    /** 按功能解析生效模型:绑定→模型行,未绑定/模型缺失返回全空(该功能未配置);type 用于区分本地/LLM */
    public AiConfig resolveForFeature(Long familyId, String featureCode) {
        if (familyId == null) return empty();
        AiFeature binding = aiFeatureMapper.selectOne(new LambdaQueryWrapper<AiFeature>()
                .eq(AiFeature::getFamilyId, familyId)
                .eq(AiFeature::getFeatureCode, featureCode));
        if (binding == null || binding.getModelId() == null) return empty();
        AiModel m = aiModelMapper.selectById(binding.getModelId());
        if (m == null || !m.getFamilyId().equals(familyId)) return empty();
        return new AiConfig(
                m.getBaseUrl(),
                m.getApiKey(),
                m.getModel(),
                m.getTimeoutMs() != null ? m.getTimeoutMs() : DEFAULT_TIMEOUT_MS,
                m.getType(),
                m.getProvider() == null || m.getProvider().isBlank() ? AiConst.PROVIDER_OPENAI : m.getProvider(),
                m.getSecretKey());
    }

    private AiConfig empty() {
        return new AiConfig(null, null, null, DEFAULT_TIMEOUT_MS, null, AiConst.PROVIDER_OPENAI, null);
    }

    // ---------- 内置 LOCAL 模型 ----------

    /**
     * 懒创建内置 LOCAL 模型(幂等):listModels/getFeatures 时确保每家庭恒有一条 LOCAL。
     * 现有家庭已由迁移 SQL 插入,此处仅兜底未来新家庭。
     */
    public AiModel ensureLocalModel(Long familyId) {
        if (familyId == null) return null;
        AiModel local = aiModelMapper.selectOne(new LambdaQueryWrapper<AiModel>()
                .eq(AiModel::getFamilyId, familyId)
                .eq(AiModel::getType, AiConst.TYPE_LOCAL)
                .last("LIMIT 1"));
        if (local != null) return local;
        AiModel m = new AiModel();
        m.setFamilyId(familyId);
        m.setName("本地规则解析(离线)");
        m.setType(AiConst.TYPE_LOCAL);
        m.setModel(LOCAL_MODEL);
        m.setTimeoutMs(DEFAULT_TIMEOUT_MS);
        m.setSortOrder(0);
        aiModelMapper.insert(m);
        return m;
    }

    // ---------- 模型池 CRUD ----------

    /** 模型池列表(密钥只回 apiKeySet 布尔,不回传;LOCAL 带 builtin=true) */
    public List<Map<String, Object>> listModels(Long familyId) {
        ensureLocalModel(familyId);
        return aiModelMapper.selectList(new LambdaQueryWrapper<AiModel>()
                        .eq(AiModel::getFamilyId, familyId)
                        .orderByAsc(AiModel::getSortOrder)
                        .orderByAsc(AiModel::getId)).stream()
                .map(this::toMap)
                .toList();
    }

    private Map<String, Object> toMap(AiModel m) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("id", m.getId());
        o.put("name", nullToEmpty(m.getName()));
        o.put("type", m.getType());
        o.put("provider", m.getProvider() == null || m.getProvider().isBlank() ? AiConst.PROVIDER_OPENAI : m.getProvider());
        o.put("baseUrl", nullToEmpty(m.getBaseUrl()));
        o.put("model", nullToEmpty(m.getModel()));
        o.put("timeoutMs", m.getTimeoutMs());
        o.put("apiKeySet", notBlank(m.getApiKey()));
        o.put("secretKeySet", notBlank(m.getSecretKey()));
        o.put("sortOrder", m.getSortOrder());
        o.put("builtin", AiConst.TYPE_LOCAL.equals(m.getType()));
        return o;
    }

    /** 新增/更新模型:id 为空新增,否则更新(校验归属);密钥留空保留原值、显式给出 ENC 加密入库;内置 LOCAL 拒绝修改 */
    public Long saveModel(Long familyId, Long id, Map<String, String> body) {
        if (body == null) throw new BizException(ResultCode.BAD_REQUEST, "请求体不能为空");
        AiModel m;
        if (id == null) {
            m = new AiModel();
            m.setFamilyId(familyId);
        } else {
            m = requireModel(id, familyId);
            if (AiConst.TYPE_LOCAL.equals(m.getType())) {
                throw new BizException(ResultCode.BAD_REQUEST, "内置本地规则模型不可修改");
            }
        }
        String name = trimToNull(body.get("name"));
        if (name == null && m.getName() == null) throw new BizException(ResultCode.BAD_REQUEST, "模型名称不能为空");
        if (name != null) m.setName(name);
        String type = trimToNull(body.get("type"));
        if (type != null) {
            if (!AiConst.isValidType(type)) throw new BizException(ResultCode.BAD_REQUEST, "模型类型须为 LLM/IMAGE/ASR");
            m.setType(type);
        }
        if (m.getType() == null) throw new BizException(ResultCode.BAD_REQUEST, "模型类型不能为空");
        String model = trimToNull(body.get("model"));
        if (model != null) m.setModel(model);
        if (m.getModel() == null || m.getModel().isBlank()) throw new BizException(ResultCode.BAD_REQUEST, "模型标识不能为空");
        // 服务商 provider 仅 ASR 有意义;非 ASR 一律 OPENAI,ASR 缺省 OPENAI
        String provider = trimToNull(body.get("provider"));
        if (provider != null && !AiConst.isValidProvider(provider)) {
            throw new BizException(ResultCode.BAD_REQUEST, "服务商须为 OPENAI/BAIDU");
        }
        if (AiConst.TYPE_ASR.equals(m.getType())) {
            m.setProvider(provider == null && m.getProvider() == null ? AiConst.PROVIDER_OPENAI : (provider != null ? provider : m.getProvider()));
        } else {
            m.setProvider(AiConst.PROVIDER_OPENAI);
        }
        applyField(m::setBaseUrl, body.get("baseUrl"));
        if (body.get("timeoutMs") != null) m.setTimeoutMs(parseTimeout(body.get("timeoutMs")));
        if (body.get("sortOrder") != null) m.setSortOrder(parseIntOr(body.get("sortOrder"), 0));
        applySecret(m::setApiKey, body.get("apiKey"));
        applySecret(m::setSecretKey, body.get("secretKey"));
        if (id == null) {
            aiModelMapper.insert(m);
        } else {
            aiModelMapper.updateById(m);
        }
        return m.getId();
    }

    /** 删除模型:内置 LOCAL 拒绝删除;其余删除后把引用它的功能绑定置 null(该功能随之停用) */
    public void deleteModel(Long familyId, Long id) {
        AiModel m = requireModel(id, familyId);
        if (AiConst.TYPE_LOCAL.equals(m.getType())) {
            throw new BizException(ResultCode.BAD_REQUEST, "内置本地规则模型不可删除");
        }
        aiModelMapper.deleteById(id);
        aiFeatureMapper.update(null, new LambdaUpdateWrapper<AiFeature>()
                .eq(AiFeature::getFamilyId, familyId)
                .eq(AiFeature::getModelId, id)
                .set(AiFeature::getModelId, null));
    }

    private AiModel requireModel(Long id, Long familyId) {
        AiModel m = aiModelMapper.selectById(id);
        if (m == null || !m.getFamilyId().equals(familyId)) {
            throw new BizException(ResultCode.NOT_FOUND, "模型不存在");
        }
        return m;
    }

    // ---------- 功能绑定 ----------

    /** 功能绑定列表(5 个功能:code/允许类型/绑定的模型信息/可用状态) */
    public List<Map<String, Object>> getFeatures(Long familyId) {
        ensureLocalModel(familyId);
        Map<String, AiFeature> bindings = aiFeatureMapper.selectList(new LambdaQueryWrapper<AiFeature>()
                        .eq(AiFeature::getFamilyId, familyId)).stream()
                .collect(Collectors.toMap(AiFeature::getFeatureCode, f -> f, (a, b) -> a));
        List<Map<String, Object>> out = new ArrayList<>();
        for (String code : AiConst.FEATURES) {
            AiFeature binding = bindings.get(code);
            AiModel model = binding != null && binding.getModelId() != null ? aiModelMapper.selectById(binding.getModelId()) : null;
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("featureCode", code);
            o.put("modelTypes", AiConst.allowedTypes(code).stream().sorted().toList());
            o.put("modelId", model != null ? model.getId() : null);
            o.put("modelName", model != null ? nullToEmpty(model.getName()) : "");
            o.put("model", model != null ? nullToEmpty(model.getModel()) : "");
            o.put("modelType", model != null ? model.getType() : null);
            AiConfig c = resolveForFeature(familyId, code);
            o.put("available", isAvailable(c));
            out.add(o);
        }
        return out;
    }

    /** 绑定/解绑功能:modelId 为空=停用该功能;校验模型归属 + 允许类型集合 */
    public void bindFeature(Long familyId, String featureCode, Long modelId) {
        if (!AiConst.isValidFeature(featureCode)) throw new BizException(ResultCode.BAD_REQUEST, "未知功能");
        AiFeature binding = aiFeatureMapper.selectOne(new LambdaQueryWrapper<AiFeature>()
                .eq(AiFeature::getFamilyId, familyId)
                .eq(AiFeature::getFeatureCode, featureCode));
        Long target = modelId;
        if (modelId != null) {
            AiModel m = requireModel(modelId, familyId);
            Set<String> allowed = AiConst.allowedTypes(featureCode);
            if (!allowed.contains(m.getType())) {
                throw new BizException(ResultCode.BAD_REQUEST, "该功能只能绑定" + String.join("/", allowed) + "类型的模型");
            }
            target = m.getId();
        }
        if (binding == null) {
            binding = new AiFeature();
            binding.setFamilyId(familyId);
            binding.setFeatureCode(featureCode);
            binding.setModelId(target);
            aiFeatureMapper.insert(binding);
        } else {
            aiFeatureMapper.update(null, new LambdaUpdateWrapper<AiFeature>()
                    .eq(AiFeature::getId, binding.getId())
                    .set(AiFeature::getModelId, target));
        }
    }

    // ---------- 字段/密钥处理 ----------

    /** 生效判定:LOCAL 恒可用(离线),其余须 baseUrl/apiKey/model 配齐;百度 ASR 额外须 secretKey */
    public boolean isAvailable(AiConfig c) {
        if (AiConst.TYPE_LOCAL.equals(c.type())) return true;
        if (!notBlank(c.baseUrl()) || !notBlank(c.apiKey()) || !notBlank(c.model())) return false;
        return !AiConst.PROVIDER_BAIDU.equals(c.provider()) || notBlank(c.secretKey());
    }

    /** 文本字段:trim 后非空入库,空串置 NULL */
    private void applyField(java.util.function.Consumer<String> setter, String value) {
        setter.accept(trimToNull(value));
    }

    /** 密钥:留空(null/空串)保留原值,显式给出则 ENC 加密入库(已是 ENC(...) 密文不重复加密) */
    private void applySecret(java.util.function.Consumer<String> setter, String value) {
        if (value == null || value.isBlank()) return;
        String v = value.trim();
        setter.accept(v.startsWith("ENC(") && v.endsWith(")") ? v : parameterService.encrypt(v));
    }

    private String trimToNull(String s) {
        if (s == null) return null;
        String v = s.trim();
        return v.isEmpty() ? null : v;
    }

    private Integer parseTimeout(String t) {
        String v = t.trim();
        if (v.isEmpty()) return null;
        try {
            int n = Integer.parseInt(v);
            return Math.min(Math.max(n, 1000), 600000);
        } catch (NumberFormatException e) {
            throw new BizException(ResultCode.BAD_REQUEST, "超时时间须为毫秒数字");
        }
    }

    private Integer parseIntOr(String s, int def) {
        if (s == null || s.trim().isEmpty()) return def;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            throw new BizException(ResultCode.BAD_REQUEST, "排序须为数字");
        }
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
