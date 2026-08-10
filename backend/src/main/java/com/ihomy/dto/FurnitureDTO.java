package com.ihomy.dto;

import lombok.Data;

/**
 * 家具参数(family_furniture):挂在房间下。
 */
@Data
public class FurnitureDTO {
    private Long roomId;
    private String name;
    private String note;
}