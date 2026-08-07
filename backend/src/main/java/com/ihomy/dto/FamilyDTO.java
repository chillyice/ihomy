package com.ihomy.dto;

import lombok.Data;

/**
 * 家庭设置表单:可部分更新(空字段跳过)。
 */
@Data
public class FamilyDTO {
    private String name;
    private String coverImage;
    private String coverText;
    private String coverSubtitle;
    private String description;
    private Integer isPublic;
}
