package com.ihomy.dto;

import lombok.Data;

/**
 * 积分商品表单:家长上架/编辑商品时的入参。
 * stock=-1 不限量,perLimit=0 不限次。
 */
@Data
public class PointsProductDTO {
    private String name;
    private String icon;
    private Integer points;
    private Integer stock;
    private Integer perLimit;
    private Integer enabled;
}