package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 植物成长日志(family_plant_log):全家共养植物的照料/收获记录,家庭内容 + 交互时间线。
 */
@Data
@TableName("family_plant_log")
public class FamilyPlantLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private Long userId;
    /** 动作:PLANT/WATER/SUN/FERTILIZE/HARVEST */
    private String action;
    /** 成员寄语(可选) */
    private String message;
    /** 系统描述文案(如「浇了水」) */
    private String detail;
    private LocalDateTime createdAt;
}
