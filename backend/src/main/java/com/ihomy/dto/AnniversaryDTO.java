package com.ihomy.dto;

import lombok.Data;

import java.time.LocalTime;
import java.util.List;

/**
 * 纪念日表单:calendar solar/lunar;memberIds 可空(空=家庭级纪念日,非空=关联的成员账号)。
 * remindDaysBefore 0/空=不提醒,1-30=提前天数;remindTime 空=默认 08:00。
 */
@Data
public class AnniversaryDTO {
    private String name;
    private String calendar;
    private Integer month;
    private Integer day;
    private Integer isLeap;
    /** 关联的家庭成员账号ID列表(可多位;空/缺省=家庭级纪念日) */
    private List<Long> memberIds;
    private Integer recurring;
    /** 提前几天提醒(0/空=不提醒) */
    private Integer remindDaysBefore;
    /** 提醒时刻(空=默认 08:00) */
    private LocalTime remindTime;
}