package com.ihomy.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 家庭设置表单:可部分更新(空字段跳过)。长度上限对齐 sys_family_info 列宽。
 */
@Data
public class FamilyDTO {
    @Size(max = 50, message = "家庭名称最多 50 字")
    private String name;
    @Size(max = 255, message = "封面图片地址过长")
    private String coverImage;
    @Size(max = 100, message = "封面文字最多 100 字")
    private String coverText;
    @Size(max = 100, message = "封面副标题最多 100 字")
    private String coverSubtitle;
    @Size(max = 200, message = "简介最多 200 字")
    private String description;
    private Integer isPublic;
    private String musicUrl;
    private String musicTitle;
    private java.math.BigDecimal weatherLat;
    private java.math.BigDecimal weatherLng;
    private String weatherCity;
}
