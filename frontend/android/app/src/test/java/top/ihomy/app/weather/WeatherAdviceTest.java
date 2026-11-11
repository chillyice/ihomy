package top.ihomy.app.weather;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * 每日天气「一句建议」的纯规则测试:锁住优先级与阈值,防止以后调阈值时把安全类提醒挤掉。
 */
public class WeatherAdviceTest {

    private static WeatherSnapshot rainy(int pop) {
        WeatherSnapshot s = new WeatherSnapshot();
        s.pop = pop;
        return s;
    }

    @Test
    public void warningAlwaysWins() {
        WeatherSnapshot s = new WeatherSnapshot();
        s.warningType = "暴雨";
        s.warningLevel = "橙色";
        s.iconDay = "302";
        s.pop = 90;
        s.tempMax = 38;
        assertEquals(WeatherAdvice.Kind.WARNING, WeatherAdvice.of(s));
    }

    @Test
    public void thunderBeforeRain() {
        WeatherSnapshot s = new WeatherSnapshot();
        s.iconDay = "302";
        s.pop = 80;
        assertEquals(WeatherAdvice.Kind.THUNDER, WeatherAdvice.of(s));
    }

    @Test
    public void snowByIcon() {
        WeatherSnapshot s = new WeatherSnapshot();
        s.iconDay = "400";
        assertEquals(WeatherAdvice.Kind.SNOW, WeatherAdvice.of(s));
    }

    @Test
    public void rainWhenIconIsRain() {
        WeatherSnapshot s = new WeatherSnapshot();
        s.iconDay = "305";
        assertEquals(WeatherAdvice.Kind.RAIN, WeatherAdvice.of(s));
    }

    @Test
    public void rainWhenProbabilityHighEvenIfIconIsSunny() {
        assertEquals(WeatherAdvice.Kind.RAIN, WeatherAdvice.of(rainy(50)));
        assertEquals(WeatherAdvice.Kind.NICE, WeatherAdvice.of(rainy(49)));
    }

    @Test
    public void fogByIcon() {
        WeatherSnapshot s = new WeatherSnapshot();
        s.iconDay = "501";
        assertEquals(WeatherAdvice.Kind.FOG, WeatherAdvice.of(s));
    }

    @Test
    public void airQualityBadByAqiOrCategory() {
        WeatherSnapshot byAqi = new WeatherSnapshot();
        byAqi.aqi = 160;
        assertEquals(WeatherAdvice.Kind.AIR_BAD, WeatherAdvice.of(byAqi));

        WeatherSnapshot byCategory = new WeatherSnapshot();
        byCategory.aqiCategory = "轻度污染";
        assertEquals(WeatherAdvice.Kind.AIR_BAD, WeatherAdvice.of(byCategory));

        WeatherSnapshot good = new WeatherSnapshot();
        good.aqi = 42;
        good.aqiCategory = "优";
        assertEquals(WeatherAdvice.Kind.NICE, WeatherAdvice.of(good));
    }

    @Test
    public void rainOutranksAirQuality() {
        WeatherSnapshot s = rainy(70);
        s.aqi = 200;
        assertEquals(WeatherAdvice.Kind.RAIN, WeatherAdvice.of(s));
    }

    @Test
    public void hotAndCold() {
        WeatherSnapshot hot = new WeatherSnapshot();
        hot.tempMax = 33;
        hot.tempMin = 26;
        assertEquals(WeatherAdvice.Kind.HOT, WeatherAdvice.of(hot));

        WeatherSnapshot cold = new WeatherSnapshot();
        cold.tempMax = 4;
        cold.tempMin = 0;
        assertEquals(WeatherAdvice.Kind.COLD, WeatherAdvice.of(cold));
    }

    @Test
    public void bigTemperatureSwing() {
        WeatherSnapshot s = new WeatherSnapshot();
        s.tempMax = 20;
        s.tempMin = 10;
        assertEquals(WeatherAdvice.Kind.BIG_DIFF, WeatherAdvice.of(s));

        s.tempMin = 11;
        assertEquals(WeatherAdvice.Kind.NICE, WeatherAdvice.of(s));
    }

    @Test
    public void strongUvAndWind() {
        WeatherSnapshot uv = new WeatherSnapshot();
        uv.uvIndex = 7;
        uv.tempMax = 30;
        uv.tempMin = 22;
        assertEquals(WeatherAdvice.Kind.UV, WeatherAdvice.of(uv));

        WeatherSnapshot wind = new WeatherSnapshot();
        wind.windScale = "4-5";
        wind.tempMax = 30;
        wind.tempMin = 22;
        assertEquals(WeatherAdvice.Kind.WINDY, WeatherAdvice.of(wind));
    }

    @Test
    public void niceWeatherFallback() {
        WeatherSnapshot s = new WeatherSnapshot();
        s.iconDay = "101";
        s.tempMax = 26;
        s.tempMin = 20;
        s.aqi = 35;
        s.uvIndex = 4;
        s.windScale = "2";
        assertEquals(WeatherAdvice.Kind.NICE, WeatherAdvice.of(s));
    }

    @Test
    public void emptySnapshotIsNiceNotCrash() {
        assertEquals(WeatherAdvice.Kind.NICE, WeatherAdvice.of(null));
        assertEquals(WeatherAdvice.Kind.NICE, WeatherAdvice.of(new WeatherSnapshot()));
    }

    @Test
    public void iconCodeGrouping() {
        assertEquals(WeatherKind.SUN, WeatherKind.of("100"));
        assertEquals(WeatherKind.SUN, WeatherKind.of("150"));
        assertEquals(WeatherKind.PARTLY, WeatherKind.of("101"));
        assertEquals(WeatherKind.PARTLY, WeatherKind.of("153"));
        assertEquals(WeatherKind.CLOUD, WeatherKind.of("104"));
        assertEquals(WeatherKind.RAIN, WeatherKind.of("305"));
        assertEquals(WeatherKind.THUNDER, WeatherKind.of("303"));
        assertEquals(WeatherKind.SNOW, WeatherKind.of("499"));
        assertEquals(WeatherKind.FOG, WeatherKind.of("502"));
        assertEquals(WeatherKind.CLOUD, WeatherKind.of(""));
        assertEquals(WeatherKind.CLOUD, WeatherKind.of("abc"));
    }
}
