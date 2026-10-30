package com.ihomy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 家谱关系绑定:只改一条关系,不碰成员的其他资料。
 * kind 语义是「fromId 是 toId 的该关系」:FATHER → to.father_id=from、MOTHER → to.mother_id=from、SPOUSE → 双向绑定。
 * 与 PUT /tree/{id} 的全量覆盖区分开,避免只改关系时把性别/生日/照片/备注等字段一并清空。
 */
@Data
public class TreeRelationDTO {

    /** 关系的主动方(父亲/母亲/配偶本人) */
    @NotNull(message = "请选择成员")
    private Long fromId;

    /** 关系的被动方(子女或配偶) */
    @NotNull(message = "请选择要关联的成员")
    private Long toId;

    /** 关系类型: FATHER 父亲 / MOTHER 母亲 / SPOUSE 配偶 */
    @NotBlank(message = "请选择关系")
    @Pattern(regexp = "^(FATHER|MOTHER|SPOUSE)$", message = "关系类型不支持,请从列表中选择")
    private String kind;
}
