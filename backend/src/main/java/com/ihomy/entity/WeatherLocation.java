package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import lombok.Data;

/**
 * 天气城市字典实体(sys_weather_location):由供应商(和风)城市库导入,供城市搜索与 LocationID 反查。
 * id 即供应商 LocationID(非自增,字符串);adm1/adm2 为省/市两级行政区划。
 */
@Data
@TableName("sys_weather_location")
public class WeatherLocation {
    @TableId
    private String id;
    private String name;
    private String adm1;
    private String adm2;
    private BigDecimal lat;
    private BigDecimal lng;
}
