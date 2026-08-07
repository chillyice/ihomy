package com.ihomy.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ihomy.annotation.RequirePermission;
import com.ihomy.common.Result;
import com.ihomy.entity.SysOperationLog;
import com.ihomy.mapper.SysOperationLogMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作日志接口:分页查询(需 log:view 权限)。
 */
@Tag(name = "操作日志")
@RestController
@RequestMapping("/log")
@RequiredArgsConstructor
public class LogController {

    private final SysOperationLogMapper logMapper;

    @Operation(summary = "操作日志分页（仅家长）")
    @RequirePermission("log:view")
    @GetMapping
    public Result<IPage<SysOperationLog>> page(@RequestParam(defaultValue = "1") int current,
                                               @RequestParam(defaultValue = "20") int size) {
        LambdaQueryWrapper<SysOperationLog> qw = new LambdaQueryWrapper<>();
        qw.orderByDesc(SysOperationLog::getCreatedAt);
        return Result.success(logMapper.selectPage(new Page<>(current, size), qw));
    }
}