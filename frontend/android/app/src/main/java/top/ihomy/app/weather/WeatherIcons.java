package top.ihomy.app.weather;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import androidx.core.content.ContextCompat;

import java.util.EnumMap;
import java.util.Map;

import top.ihomy.app.R;

/**
 * 通知大图标:按天气分档取一枚彩色圆底矢量,渲染成 Bitmap。
 *
 * <p>通知的大图标只吃 Bitmap(不吃 drawable 资源),所以这里做一次光栅化并按分档缓存——
 * 一天最多几张,不需要更复杂的缓存策略。
 */
public final class WeatherIcons {

    /** 大图标边长(px,xxhdpi 下约 64dp),通知栏会再缩放显示 */
    private static final int SIZE = 192;

    private static final Map<WeatherKind, Integer> RES = new EnumMap<>(WeatherKind.class);
    private static final Map<WeatherKind, Bitmap> CACHE = new EnumMap<>(WeatherKind.class);

    static {
        RES.put(WeatherKind.SUN, R.drawable.ic_weather_sun);
        RES.put(WeatherKind.PARTLY, R.drawable.ic_weather_partly);
        RES.put(WeatherKind.CLOUD, R.drawable.ic_weather_cloud);
        RES.put(WeatherKind.RAIN, R.drawable.ic_weather_rain);
        RES.put(WeatherKind.THUNDER, R.drawable.ic_weather_thunder);
        RES.put(WeatherKind.SNOW, R.drawable.ic_weather_snow);
        RES.put(WeatherKind.FOG, R.drawable.ic_weather_fog);
    }

    private WeatherIcons() {
    }

    /** 图标码 → 大图标 Bitmap;取不到时给多云,保证通知总有图不会空一块 */
    public static Bitmap largeIcon(Context context, String iconCode) {
        WeatherKind kind = WeatherKind.of(iconCode);
        synchronized (CACHE) {
            Bitmap cached = CACHE.get(kind);
            if (cached != null) return cached;
            Bitmap bitmap = raster(context, RES.getOrDefault(kind, R.drawable.ic_weather_cloud));
            CACHE.put(kind, bitmap);
            return bitmap;
        }
    }

    private static Bitmap raster(Context context, int res) {
        Drawable d = ContextCompat.getDrawable(context, res);
        Bitmap bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        if (d == null) return bitmap;
        Canvas canvas = new Canvas(bitmap);
        d.setBounds(0, 0, SIZE, SIZE);
        d.draw(canvas);
        return bitmap;
    }
}
