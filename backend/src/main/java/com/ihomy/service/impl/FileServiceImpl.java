package com.ihomy.service.impl;

import com.ihomy.service.FileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 文件存储实现(本地磁盘):按日期分目录存放,
 * 文件名用 UUID 防冲突,返回可经 /files/ 访问的 URL。
 */
@Slf4j
@Service
public class FileServiceImpl implements FileService {

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${file.url-prefix}")
    private String urlPrefix;

    /** 保存文件到 upload-dir/日期目录,保留原扩展名,返回访问 URL */
    @Override
    public String upload(byte[] bytes, String originalName, String contentType) {
        try {
            String dateDir = LocalDate.now().toString();
            Path dir = Paths.get(uploadDir, dateDir);
            Files.createDirectories(dir);
            String ext = "";
            if (originalName != null && originalName.contains(".")) {
                ext = originalName.substring(originalName.lastIndexOf("."));
            }
            String fileName = UUID.randomUUID().toString().replace("-", "") + ext;
            Path target = dir.resolve(fileName);
            Files.write(target, bytes);
            return urlPrefix + "/" + dateDir + "/" + fileName;
        } catch (IOException e) {
            log.error("文件上传失败", e);
            throw new RuntimeException("文件上传失败");
        }
    }

    @Override
    public String getUrlPrefix() {
        return urlPrefix;
    }
}
