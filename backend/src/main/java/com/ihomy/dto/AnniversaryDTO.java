package com.ihomy.dto;

import lombok.Data;

/**
 * 纪念日表单:calendar solar/lunar,userId 可空(家庭级纪念日)。
 */
@Data
public class AnniversaryDTO {
    private String name;
    private String calendar;
    private Integer month;
    private Integer day;
    private Integer isLeap;
    private Long userId;
    private Integer recurring;
}