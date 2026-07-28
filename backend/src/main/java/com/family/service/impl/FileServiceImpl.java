package com.family.service.impl;

import com.family.service.FileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@Service
public class FileServiceImpl implements FileService {

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${file.url-prefix}")
    private String urlPrefix;

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
