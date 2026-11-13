package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户界面偏好实体(sys_user_pref):首页布局这类「跟人走」的前端偏好上云,换设备登录即恢复。
 * pref_value 是不透明 JSON 文本,服务端不解析结构;布局语义升级靠键里的版本号(如 home.layout.v3)。
 * 不带 family_id(换家庭也该跟着人走),也不做逻辑删除(恢复默认是覆盖写)。
 */
@Data
@TableName("sys_user_pref")
public class SysUserPref {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String prefKey;
    private String prefValue;
    private LocalDateTime createdAt;
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
