package com.ihomy.service;

/**
 * 文件存储服务接口:上传返回可访问 URL。
 */
public interface FileService {
    String upload(byte[] bytes, String originalName, String contentType);

    String getUrlPrefix();
}
