package top.ihomy.app.weather;

/**
 * 每日天气推送需要的最小字段集合(纯数据,无 Android 依赖,便于单测)。
 *
 * <p>字段可缺:null / 空串 = 上游没给这一项,展示时整段省略,不写「0°」「0%」这类假数据。
 * 解析在 {@link DailyWeatherRepository},这里只承载口径。
 */
public final class WeatherSnapshot {

    /** 城市名(家庭天气偏好位置优先,未设置时服务端按请求 IP 取) */
    public String city = "";
    /** 实况天气文字,如「晴」 */
    public String nowText = "";
    /** 白天天气文字,如「多云转晴」(标题优先用它) */
    public String dayText = "";
    /** 白天天气图标码(和风 v1),用于图标与建议分档 */
    public String iconDay = "";
    /** 实况温度(°) */
    public Integer nowTemp;
    /** 体感温度(°) */
    public Integer feelsLike;
    /** 今日最高/最低(°) */
    public Integer tempMax;
    public Integer tempMin;
    /** 白天降水概率(%) */
    public Integer pop;
    /** 风向/风力(白天,缺则回落实况) */
    public String windDir = "";
    public String windScale = "";
    /** 空气质量等级文字(如「优」「轻度污染」)与 AQI 数值 */
    public String aqiCategory = "";
    public Integer aqi;
    /** 紫外线指数 */
    public Integer uvIndex;
    /** 当前生效预警的类型与等级(如「暴雨」「橙色」),无预警为空 */
    public String warningType = "";
    public String warningLevel = "";
}
