package com.ihomy.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 上传后自动生成照片标题描述的异步执行器:@Async 必须跨 bean 调用才生效,业务体在 PhotoAiService。
 * 逐张串行,单张失败只记日志——自动生成是锦上添花,不能影响上传结果与后续照片。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PhotoAiRunner {

    private final PhotoAiService photoAiService;

    @Async
    public void autoCaption(Long familyId, List<Long> photoIds) {
        if (photoIds == null || photoIds.isEmpty()) return;
        for (Long id : photoIds) {
            try {
                photoAiService.autoCaption(familyId, id);
            } catch (Exception e) {
                log.warn("[照片AI] 自动生成失败 photoId={} err={}", id, e.getMessage());
            }
        }
    }
}
