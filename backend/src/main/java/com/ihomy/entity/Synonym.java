package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 同义词实体(sys_synonym):全局可生长的「规范词→别名」映射,
 * 物品录入联想别名 + 找物读表扩展共用。source 标记来源:BUILTIN/LLM/USER。
 */
@Data
@TableName("sys_synonym")
public class Synonym {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 规范词(如 纸巾) */
    private String canonical;
    /** 同义别名(如 手纸) */
    private String alias;
    /** 来源:BUILTIN/LLM/USER */
    private String source;
    private LocalDateTime createdAt;
}
