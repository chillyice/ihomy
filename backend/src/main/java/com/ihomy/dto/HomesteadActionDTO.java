package com.ihomy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 小院慢生活:切换挂机动作。动作门槛/材料由服务端校验,前端传的只是意图。 */
@Data
public class HomesteadActionDTO {
    @NotBlank(message = "动作不能为空")
    @Size(max = 32, message = "动作编码过长")
    private String actionCode;
}
