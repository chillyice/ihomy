package com.ihomy.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** 小院慢生活:穿 / 脱装备。gearCode 为空表示脱下 slotCode 那个部位。 */
@Data
public class HomesteadWearDTO {
    @Size(max = 16, message = "部位编码过长")
    private String slotCode;

    @Size(max = 24, message = "装备编码过长")
    private String gearCode;
}
