package com.ihomy.service;

import com.ihomy.entity.StorageDevice;
import com.ihomy.entity.SysUser;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 音乐映射任务异步执行器:@Async 必须跨 bean 调用才生效,业务体在 MusicMapService。
 */
@Component
@RequiredArgsConstructor
public class MusicMapRunner {

    private final MusicMapService musicMapService;

    @Async
    public void runMapping(Long taskId, SysUser user, Long familyId, StorageDevice device, List<String> paths) {
        musicMapService.executeMapping(taskId, user, familyId, device, paths);
    }

    @Async
    public void runRefresh(Long taskId, SysUser user, Long familyId, Map<Long, Set<String>> dirsByDevice) {
        musicMapService.executeRefresh(taskId, user, familyId, dirsByDevice);
    }
}
