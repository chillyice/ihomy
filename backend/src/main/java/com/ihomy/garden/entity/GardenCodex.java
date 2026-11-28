package com.ihomy.garden.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 花园图鉴(games.garden_codex):首次收获记录,永不删除、不受日志清理影响(FR-18、架构分册 §16.4 要点 3)。
 */
@Data
@TableName("games.garden_codex")
public class GardenCodex {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private String cropCode;
    private LocalDateTime firstHarvestAt;
    private Integer harvestCnt;
}
