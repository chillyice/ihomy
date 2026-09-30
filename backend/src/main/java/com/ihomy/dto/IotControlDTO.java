package com.ihomy.dto;

import lombok.Data;

import java.util.Map;

/**
 * 智能设备控制请求:entityId 即 HA 实体ID(域由后缀推导),service 为 HA 服务名(白名单校验),
 * data 为该服务的附加参数(如 set_temperature 的 temperature)。
 */
@Data
public class IotControlDTO {
    private String entityId;
    private String service;
    private Map<String, Object> data;
}
