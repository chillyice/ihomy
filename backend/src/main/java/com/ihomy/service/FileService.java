package com.ihomy.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 文件存储(本地磁盘,V4.1 统一目录):
 * 系统上传一律走 {uploadDir}/upload/{yyyyMM}/{相册ID}_{时间戳}_{原文件名}(细分类型+时间+相册ID),
 * 无相册 ID 时文件名不带前缀;返回可经 /files/ 访问的 URL。
 */
@Slf4j
@Service
public class FileService {

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${file.url-prefix}")
    private String urlPrefix;

    /** 通用上传(无相册 ID) */
    public String upload(byte[] bytes, String originalName, String contentType) {
        return upload(bytes, originalName, contentType, null);
    }

    /** 保存文件到 upload-dir/upload/yyyyMM/目录,保留原扩展名,返回访问 URL */
    public String upload(byte[] bytes, String originalName, String contentType, Long albumId) {
        try {
            String yyyyMM = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
            Path dir = Paths.get(uploadDir, "upload", yyyyMM);
            Files.createDirectories(dir);
            String base = originalName == null ? "file" : originalName.replaceAll("[^\\w.\\-\\u4e00-\\u9fa5]", "_");
            String fileName = (albumId == null ? "" : albumId + "_") + System.currentTimeMillis() + "_" + base;
            Path target = dir.resolve(fileName);
            Files.write(target, bytes);
            return urlPrefix + "/upload/" + yyyyMM + "/" + fileName;
        } catch (IOException e) {
            log.error("文件上传失败", e);
            throw new RuntimeException("文件上传失败");
        }
    }
}