package com.ihomy.common;

import java.util.List;

/**
 * 天气源(provider)常量(V9.53 起多天气源):provider code 与展示顺序。
 * 新增天气源三步:①此处加 code 并进 PROVIDERS;②新增 WeatherProvider 实现类;③前端下拉加一项。
 */
public final class WeatherConst {

    private WeatherConst() {
    }

    public static final String PROVIDER_QWEATHER = "QWEATHER";
    public static final String PROVIDER_OPENWEATHER = "OPENWEATHER";
    public static final String PROVIDER_AMAP = "AMAP";

    /** 天气源展示顺序(前端下拉按此渲染) */
    public static final List<String> PROVIDERS = List.of(
            PROVIDER_QWEATHER, PROVIDER_OPENWEATHER, PROVIDER_AMAP);

    public static boolean isValidProvider(String code) {
        return PROVIDERS.contains(code);
    }
}
