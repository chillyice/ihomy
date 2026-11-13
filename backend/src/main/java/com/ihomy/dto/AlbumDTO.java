package com.ihomy.dto;

import lombok.Data;

/**
 * 相册表单:type public/private(缺省 public);visibility 为新四档口径(缺省按 type 推导)。
 */
@Data
public class AlbumDTO {
    private String name;
    private String type;
    /** 可见范围 PRIVATE/CIRCLE/FAMILY/PUBLIC;空=按 type 推导(private→PRIVATE, public→PUBLIC) */
    private String visibility;
}