package com.ihomy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 亲友圈表单:建圈/改圈名只用到 name(圈名)与 coverImage(可选封面)。
 */
@Data
public class CircleDTO {

    @NotBlank(message = "请填写圈名")
    @Size(max = 50, message = "圈名最多 50 个字")
    private String name;

    @Size(max = 500, message = "封面地址过长")
    private String coverImage;
}
