package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 小院慢生活工具档(game_homestead_tool):一人一工具一行,0 阶 = 徒手。
 * 每阶 +8% 动作速度(引擎常量 TOOL_STEP_BP),升阶花费在 content/homestead/tools.json。
 */
@Data
@TableName("game_homestead_tool")
public class HomesteadTool {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String toolCode;
    private Integer tier;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
