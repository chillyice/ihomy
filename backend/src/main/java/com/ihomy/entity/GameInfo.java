package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 家庭小游戏实体(game_info):导入的 swf/gba 小游戏与外部网页游戏(LINK),家庭隔离,可改名/改描述/删除。
 */
@Data
@TableName("game_info")
public class GameInfo {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private Long userId;
    private String name;
    private String description;
    private String type;
    /** 分类(ACTION/PUZZLE/.../OTHER),见 common/GameCategory */
    private String category;
    /** 游戏系列(手动填写或按名称自动归类),见 common/GameSeries */
    private String series;
    private String fileUrl;
    /** 外部网页游戏地址(仅 LINK 类型有值) */
    private String linkUrl;
    private String status;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createdAt;
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
    /** 当前成员是否已保存该游戏的站点账号(列表查询内存回填,非表字段) */
    @TableField(exist = false)
    private Boolean hasAccount;
}
