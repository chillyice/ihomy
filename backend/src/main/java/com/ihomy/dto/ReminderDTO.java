package com.ihomy.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 提醒事项表单。
 * repeatType:ONCE 一次性 / DAILY 每日 / WEEKLY 每周 / MONTHLY 每月 / YEARLY 每年;
 * 每年重复时 calendar 选 solar 阳历或 lunar 农历,农历须给 lunarMonth/lunarDay(与 isLeap)。
 */
@Data
public class ReminderDTO {
    private String title;
    private String content;
    /** 基准日期:一次性=触发当天,每年=锚定公历日 */
    private LocalDate remindDate;
    private LocalTime remindTime;
    private String repeatType;
    /** 历法:solar 阳历 / lunar 农历 */
    private String calendar;
    private Integer lunarMonth;
    private Integer lunarDay;
    /** 农历是否闰月:0 否 / 1 是 */
    private Integer isLeap;
}
