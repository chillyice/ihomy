package com.ihomy.service;

import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 目录映射任务进度注册表(内存 map,重启丢失,低频后台任务可接受)。
 * 相册映射与视频映射共用,进度统一经 GET /storage/sync/progress/{taskId} 查询,
 * 前端 SyncDialog/stores-sync 无需区分目标类型。
 */
@Component
public class MapTaskRegistry {

    private final Map<Long, Map<String, Object>> tasks = new ConcurrentHashMap<>();

    /** 开始任务,返回可写的进度 map */
    public Map<String, Object> begin(Long taskId) {
        return tasks.computeIfAbsent(taskId, k -> new ConcurrentHashMap<>());
    }

    public Map<String, Object> progress(Long taskId) {
        Map<String, Object> p = tasks.get(taskId);
        if (p == null) throw new BizException(ResultCode.NOT_FOUND, "任务不存在或已过期");
        return p;
    }
}
