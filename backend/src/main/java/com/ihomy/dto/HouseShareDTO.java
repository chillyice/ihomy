package com.ihomy.dto;

import lombok.Data;

import java.util.List;

/** 房子共享设置:全量替换被共享家庭清单(空数组=撤销全部共享)。 */
@Data
public class HouseShareDTO {
    private List<Long> familyIds;
}
