package com.ihomy.dto;

import lombok.Data;

import java.util.List;

/**
 * 健康数据统一上报(唯一写入口):iOS 快捷指令 / Android Health Connect / 后续 iOS 原生包共用。
 * 睡眠一律传「分期区间」,由服务端聚合会话(三端同一口径);其余指标按 POINT/DAY 传样本。
 */
@Data
public class HealthIngestDTO {

    /** SHORTCUT / HEALTH_CONNECT / IOS_NATIVE */
    private String provider;
    /** 上报端时区偏移,如 +08:00 */
    private String timezone;
    private List<Sample> samples;
    private List<SleepInterval> sleep;

    @Data
    public static class Sample {
        private String metric;
        /** POINT(单点或小时聚合)/ DAY */
        private String period;
        /** DAY:当地日 yyyy-MM-dd */
        private String date;
        /** POINT 单点时刻 */
        private String at;
        /** POINT 区段(小时聚合),与 end + avg 搭配 */
        private String start;
        private String end;
        /** 单值或小时聚合平均值 */
        private Double value;
        private Double avg;
        private Double min;
        private Double max;
        private Integer count;
        /** 设备厂商:GARMIN/HUAWEI/APPLE/... */
        private String source;
        /** 来源 App(Health Connect 包名 / 快捷指令固定 APPLE_HEALTH) */
        private String sourceApp;
    }

    @Data
    public static class SleepInterval {
        private String start;
        private String end;
        /** AWAKE/LIGHT/DEEP/REM/INBED/UNKNOWN */
        private String stage;
        private String source;
        private String sourceApp;
    }
}
