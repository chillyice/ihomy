package com.ihomy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 新建/编辑备份策略:模块多选 + 周期 + 可选保留天数 + 启停。 */
@Data
public class BackupPolicyDTO {

    @NotBlank(message = "策略名称不能为空")
    @Size(max = 60, message = "策略名称不能超过 60 个字符")
    private String name;

    @NotEmpty(message = "至少选择一个备份模块")
    @Size(max = 20, message = "备份模块过多")
    private List<String> modules;

    @NotBlank(message = "执行周期不能为空")
    private String scheduleType;

    @NotBlank(message = "执行时间不能为空")
    private String scheduleTime;

    /** 每周:1-7(周一=1);每月:1-28 */
    private Integer scheduleDay;

    /** 留空 = 不自动清理 */
    @Min(value = 1, message = "保留天数至少 1 天")
    @Max(value = 3650, message = "保留天数最多 3650 天")
    private Integer retentionDays;

    private Boolean enabled;
}
