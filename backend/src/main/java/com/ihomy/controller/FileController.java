package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.common.Result;
import com.ihomy.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 文件上传接口:统一上传入口,返回可访问 URL(供头像/封面/内容配图使用)。
 */
@Tag(name = "文件上传")
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @Operation(summary = "上传图片/封面")
    @OperationLog(module = "FILE", operationType = "CREATE", description = "上传文件", saveArgs = false)
    @PostMapping("/upload")
    public Result<Map<String, String>> upload(@RequestParam("file") MultipartFile file) throws IOException {
        String url = fileService.upload(file.getBytes(), file.getOriginalFilename(), file.getContentType());
        Map<String, String> data = new HashMap<>();
        data.put("url", url);
        return Result.success(data);
    }
}
