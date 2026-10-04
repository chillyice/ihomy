package com.ihomy.dto;

import lombok.Data;

import java.util.List;

/**
 * 纪念日表单:calendar solar/lunar;memberIds 可空(空=家庭级纪念日,非空=关联的成员账号)。
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
}