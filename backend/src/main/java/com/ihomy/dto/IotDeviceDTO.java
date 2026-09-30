package com.ihomy.dto;

import lombok.Data;

/** 智能设备展示属性修改(显示名/所属房间/是否展示),来源设备与状态由同步维护,不可改。 */
@Data
public class IotDeviceDTO {
    private String name;
    private String room;
    private Boolean enabled;
}
