package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_operation_log")
public class SysOperationLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long operatorId;
    private String operatorName;
    private String operationType;
    private String module;
    private String description;
    private String requestMethod;
    private String requestUrl;
    private String requestParams;
    private Integer resultStatus;
    private String errorMsg;
    private String ip;
    private Long costTime;
    private LocalDateTime createdAt;
}
