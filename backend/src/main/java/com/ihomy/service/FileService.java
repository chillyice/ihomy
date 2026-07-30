package com.ihomy.service;

public interface FileService {
    String upload(byte[] bytes, String originalName, String contentType);

    String getUrlPrefix();
}
