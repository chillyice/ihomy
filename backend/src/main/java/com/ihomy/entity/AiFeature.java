package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 家庭级 AI 功能绑定实体(sys_family_ai_feature):每家庭每功能一行,
 * model_id 指向 sys_family_ai_model.id,null=该功能未配置(停用)。
 */
@Data
@TableName("sys_family_ai_feature")
public class AiFeature {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    /** 功能 code(ITEM_FIND/ITEM_PUT/CHAT/IMAGE/ASR,见 AiConst) */
    private String featureCode;
    /** 绑定的模型 id,可空=未配置 */
    private Long modelId;
}
