package com.ihomy.common;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI 模型池与功能绑定常量(V9.48;V9.49 加 LOCAL 本地规则):feature_code 与 model type。
 * 功能只能绑定其允许类型集合内的模型(FEATURE_ALLOWED_TYPES 做绑定校验);
 * 找物/放物允许绑「本地规则(LOCAL)」或「语言模型(LLM)」,实现本地优先 + LLM 兜底。
 */
public final class AiConst {

    private AiConst() {
    }

    /** 模型类型 */
    public static final String TYPE_LLM = "LLM";
    public static final String TYPE_IMAGE = "IMAGE";
    public static final String TYPE_ASR = "ASR";
    /** 本地规则(内置模型条目,不可删改;零 token 离线解析) */
    public static final String TYPE_LOCAL = "LOCAL";

    /** 功能 code */
    public static final String FEATURE_ITEM_FIND = "ITEM_FIND";
    public static final String FEATURE_ITEM_PUT = "ITEM_PUT";
    public static final String FEATURE_CHAT = "CHAT";
    public static final String FEATURE_IMAGE = "IMAGE";
    public static final String FEATURE_ASR = "ASR";

    /** 功能 → 允许绑定的模型类型集合(绑定校验用;找物/放物本地或 LLM 二选一) */
    public static final Map<String, Set<String>> FEATURE_ALLOWED_TYPES = Map.of(
            FEATURE_ITEM_FIND, Set.of(TYPE_LOCAL, TYPE_LLM),
            FEATURE_ITEM_PUT, Set.of(TYPE_LOCAL, TYPE_LLM),
            FEATURE_CHAT, Set.of(TYPE_LLM),
            FEATURE_IMAGE, Set.of(TYPE_IMAGE),
            FEATURE_ASR, Set.of(TYPE_ASR));

    /** 功能展示顺序(设置页绑定区按此顺序渲染) */
    public static final List<String> FEATURES = List.of(
            FEATURE_ITEM_FIND, FEATURE_ITEM_PUT, FEATURE_CHAT, FEATURE_IMAGE, FEATURE_ASR);

    public static boolean isValidFeature(String code) {
        return FEATURE_ALLOWED_TYPES.containsKey(code);
    }

    /** 允许的功能类型集合(绑定校验与前端下拉过滤共用) */
    public static Set<String> allowedTypes(String featureCode) {
        return FEATURE_ALLOWED_TYPES.getOrDefault(featureCode, Set.of());
    }

    /** 用户可创建的模型类型(LOCAL 为内置保留,不通过 API 创建) */
    public static boolean isValidType(String type) {
        return TYPE_LLM.equals(type) || TYPE_IMAGE.equals(type) || TYPE_ASR.equals(type);
    }
}
