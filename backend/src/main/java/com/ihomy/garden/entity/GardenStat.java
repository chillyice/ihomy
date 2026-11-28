package com.ihomy.garden.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 每日计数快照(games.garden_stat):只存原始计数,等级 / 任务 / 校准由它推导不落库
 * (架构分册 §16.4 要点 10;与 garden_codex、日志清理三者同批上线,§5.5)。
 */
@Data
@TableName("games.garden_stat")
public class GardenStat {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private LocalDate statDate;
    private Integer careCnt;
    private Integer plantCnt;
    private Integer harvestCnt;
    private Integer coinIn;
    private Integer coinOut;
    private Integer pointsOut;
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
