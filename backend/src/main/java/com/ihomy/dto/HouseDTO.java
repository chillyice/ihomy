package com.ihomy.dto;

import lombok.Data;

/**
 * 房子参数(family_house):物品定位第2级粒度。
 */
@Data
public class HouseDTO {
    private String name;
    private String address;
    private Integer sortOrder;
}