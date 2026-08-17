package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import lombok.Data;

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
