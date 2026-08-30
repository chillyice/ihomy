package com.ihomy.service;

import com.ihomy.entity.Album;
import com.ihomy.entity.StorageDevice;
import com.ihomy.entity.SysUser;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 映射任务异步执行器:@Async 必须跨 bean 调用才生效,业务体在 AlbumMapService。
 */
@Component
@RequiredArgsConstructor
public class AlbumMapRunner {

    private final AlbumMapService albumMapService;

    @Async
    public void runMapping(Long taskId, SysUser user, Long familyId, StorageDevice device, List<String> paths) {
        albumMapService.executeMapping(taskId, user, familyId, device, paths);
    }

    @Async
    public void runRefresh(Long taskId, SysUser user, Long familyId, StorageDevice device,
                           Album album, boolean recursive) {
        albumMapService.executeRefresh(taskId, user, familyId, device, album, recursive);
    }
}
