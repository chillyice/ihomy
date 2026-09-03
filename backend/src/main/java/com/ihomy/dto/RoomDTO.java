package com.ihomy.dto;

import lombok.Data;

/**
 * 房间参数(family_room):挂在房子下,2期户型图将在此基础上扩展坐标字段。
 */
@Data
public class RoomDTO {
    private Long houseId;
    private String name;
    private Integer floor;
    private Integer sortOrder;
    private String note;
    private String geometry;
}