package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 开源组件台账实体(sys_oss_component):登记 ihomy 集成的开源软件(直接依赖 + 独立服务)。
 * 版本检测纯规则实现(无 AI),升级提示见 /ops/oss。
 */
@Data
@TableName("sys_oss_component")
public class OssComponent {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 显示名,如 "Element Plus" */
    private String name;
    /** 组件类型:NPM / MAVEN / SERVICE */
    private String componentType;
    /** 包引用:NPM 用包名、MAVEN 用 group:artifact、SERVICE 用 owner/repo */
    private String packageRef;
    /** 当前使用/运行版本(SERVICE 由管理员维护) */
    private String currentVersion;
    /** 检测到的最新稳定版 */
    private String latestVersion;
    /** 更新类型:MAJOR / MINOR / PATCH / NONE */
    private String updateType;
    private String license;
    /** 主页/仓库链接(查看变更) */
    private String repoUrl;
    /** 用途说明(集成在哪 / 部分功能) */
    private String purpose;
    /** 集成状态:FULL / PARTIAL / PLANNED */
    private String integrationStatus;
    /** 状态:ACTIVE / IGNORED(忽略后不再提示) */
    private String status;
    private LocalDateTime lastCheckedAt;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
