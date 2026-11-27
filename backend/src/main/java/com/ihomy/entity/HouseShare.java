package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 房子协作共享(family_house_share):把一所房子授权给另一个家庭共同维护。
 * 一行 = 一条授权,撤销即物理删除;房子与子数据仍归房主家庭,只记授权人。
 */
@Data
@TableName("family_house_share")
public class HouseShare {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long houseId;
    private Long granteeFamilyId;
    private Long createdBy;
    private LocalDateTime createdAt;
}
