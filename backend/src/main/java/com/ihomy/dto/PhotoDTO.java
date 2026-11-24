package com.ihomy.dto;

import lombok.Data;

/**
 * 照片表单:修改标题与备注(两者都可单独更新,空串表示清空)。
 */
@Data
public class PhotoDTO {
    @jakarta.validation.constraints.Size(max = 100, message = "标题最多 100 个字")
    private String title;
    @jakarta.validation.constraints.Size(max = 255, message = "描述最多 255 个字")
    private String description;
}