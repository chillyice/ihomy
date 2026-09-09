package com.ihomy.service;

import com.ihomy.entity.WeatherCredential;

import java.util.List;
import java.util.Map;

/**
 * 天气源适配器(V9.53 起多天气源):每个天气源一个实现类,由 Spring 收集进 List,WeatherService 按
 * 凭证的 provider 分发。返回的 Map 一律归一化为前端既有的字段形状(current/detail 形状与和风一致),
 * 前端零改动;字段缺失时返回部分数据或空,WeatherService/前端会降级。
 *
 * 新增天气源:实现本接口 + 在 WeatherConst.PROVIDERS 加 code + 前端下拉加一项即可,不改 WeatherService。
 */
public interface WeatherProvider {

    /** 天气源 code(与 WeatherConst.PROVIDER_* 一致) */
    String code();

    /**
     * 当前天气:归一化 Map 至少含 {condition, temp, text, iconCode, nowFull};失败返回 null。
     * coords 为 "lng,lat"(WeatherService 已解析好坐标,含 IP 定位/家庭偏好)。
     */
    Map<String, Object> current(String coords, WeatherCredential cred);

    /**
     * 天气详情:归一化 Map 含 {daily, hourly, warning, air, indices, minutely} 中支持的字段;
     * 失败返回 null(部分字段可缺)。
     */
    Map<String, Object> detail(String coords, WeatherCredential cred);

    /** 当前生效预警列表(供 30 分钟一轮的主动推送);非和风默认空。 */
    default List<Map<String, Object>> alerts(String coords, WeatherCredential cred) {
        return List.of();
    }

    /** 财务汇总(和风控制台专用);其余天气源返回不支持提示。 */
    default Map<String, Object> finance(WeatherCredential cred) {
        return Map.of("error", "当前天气源不支持财务查询");
    }

    /** 请求量统计(和风控制台专用);其余天气源返回不支持提示。 */
    default Map<String, Object> stats(WeatherCredential cred) {
        return Map.of("error", "当前天气源不支持请求量统计");
    }
}
