package com.ihomy.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 凭邀请码加入亲友圈 */
@Data
public class CircleJoinDTO {

    @NotBlank(message = "请填写邀请码")
    private String code;
}
