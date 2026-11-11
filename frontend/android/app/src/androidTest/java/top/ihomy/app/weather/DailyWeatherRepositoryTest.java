package top.ihomy.app.weather;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * 详情响应解析的设备内回归:夹具取自生产 /public/weather/detail 的真实形状(只留用到的字段)。
 * 设备内跑是因为 {@code org.json} 在纯 JVM 单测里是桩实现。
 */
@RunWith(AndroidJUnit4.class)
public class DailyWeatherRepositoryTest {

    /** 真实响应里 now 嵌着自己的 nowFull,顶层还有一份 nowFull;两份都要能读 */
    private static final String DATA = "{"
            + "\"nowFull\":{\"temp\":12.1,\"feelsLike\":7.7,\"icon\":\"100\",\"text\":\"晴\","
            + "\"windDir\":\"东风\",\"windScale\":\"3\"},"
            + "\"now\":{\"nowFull\":{\"temp\":12.1,\"feelsLike\":7.7,\"icon\":\"100\",\"text\":\"晴\","
            + "\"windDir\":\"东风\",\"windScale\":\"3\"},"
            + "\"temp\":12,\"city\":\"杭州\",\"iconCode\":\"100\",\"text\":\"晴\"},"
            + "\"daily\":[{\"fxDate\":\"2026-11-11\",\"tempMax\":15,\"tempMin\":7,\"iconDay\":\"100\","
            + "\"textDay\":\"晴\",\"textNight\":\"少云\",\"windDirDay\":\"南风\",\"windScaleDay\":\"3\","
            + "\"pop\":0,\"uvIndex\":\"3\"}],"
            + "\"air\":{\"aqi\":\"38\",\"category\":\"优\",\"level\":\"1\"},"
            + "\"warning\":null"
            + "}";

    @Test
    public void parsesRealDetailShape() throws Exception {
        WeatherSnapshot s = DailyWeatherRepository.parse(new JSONObject(DATA));
        assertEquals("杭州", s.city);
        assertEquals(Integer.valueOf(12), s.nowTemp);
        assertEquals(Integer.valueOf(8), s.feelsLike);
        assertEquals("晴", s.nowText);
        assertEquals("晴", s.dayText);
        assertEquals("100", s.iconDay);
        assertEquals(Integer.valueOf(15), s.tempMax);
        assertEquals(Integer.valueOf(7), s.tempMin);
        assertEquals(Integer.valueOf(0), s.pop);
        assertEquals("南风", s.windDir);
        assertEquals("3", s.windScale);
        assertEquals("优", s.aqiCategory);
        assertEquals(Integer.valueOf(38), s.aqi);
        assertEquals(Integer.valueOf(3), s.uvIndex);
        assertEquals("", s.warningType);
        // 这组真实数据应落到「天气不错」,而不是被某种阈值误判
        assertEquals(WeatherAdvice.Kind.NICE, WeatherAdvice.of(s));
    }

    @Test
    public void missingFieldsStayNullInsteadOfZero() throws Exception {
        WeatherSnapshot s = DailyWeatherRepository.parse(new JSONObject(
                "{\"now\":{\"temp\":9,\"text\":\"多云\",\"iconCode\":\"101\"}}"));
        assertEquals(Integer.valueOf(9), s.nowTemp);
        assertNull("没有预报就不该编造最高温", s.tempMax);
        assertNull("没有预报就不该编造降水概率", s.pop);
        assertNull("没有空气数据就不该写 0", s.aqi);
        assertEquals("", s.city);
    }
}
