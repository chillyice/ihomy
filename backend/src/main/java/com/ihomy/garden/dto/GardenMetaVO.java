package com.ihomy.garden.dto;

import lombok.Data;

import java.util.Map;

/**
 * 能力协商(架构分册 §28.2):客户端按 capabilities 渲染,能力缺失不渲染入口(不置灰)。
 * capabilities 只管游戏内功能,不含「首页便签」位(FR-32);登录后 world 给出世界摘要。
 */
@Data
public class GardenMetaVO {
    private String protocolVersion = "1.0";
    private String platform = "IHOMY";
    private String contentVersion;
    /** points/fridge/weather/notify/photo/registry/realtime/p2pSignal(最小闭环均为 false,除 notify 通道可用) */
    private Map<String, Object> capabilities;
    /** 登录成员返回 {worldId, kind, origin, authority, cycleStartAt};匿名返回 null */
    private Map<String, Object> world;
}
