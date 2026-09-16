package com.ihomy.dto;

import lombok.Data;

/**
 * 植物操作请求:品种(SUNFLOWER/ROSE/SUCCULENT)、成员寄语(可选)。
 */
@Data
public class PlantDTO {
    private String species;
    /** 照料/收获时的寄语(可选,家庭内容) */
    private String message;
}
