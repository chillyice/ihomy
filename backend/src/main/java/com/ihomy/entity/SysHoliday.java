package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

/**
 * 法定节假日与调休实体(sys_holiday):全局数据,不区分家庭。
 * 一行一天,type 为 HOLIDAY(放假)或 WORKDAY(补班);按年维护,缺年份时日历照常显示节日/节气。
 */
@Data
@TableName("sys_holiday")
public class SysHoliday {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 年份 */
    private Integer year;

    /** 日期 */
    private LocalDate holidayDate;

    /** 名称(如 国庆节、春节补班) */
    private String name;

    /** 类型: HOLIDAY 放假 / WORKDAY 补班 */
    private String type;
}
