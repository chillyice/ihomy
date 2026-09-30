package com.ihomy.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 文件读取鉴权:按文件 URL 反查内容表,判定游客(无登录态)是否可读。
 * 新增带文件 URL 的内容表时必须补进 FileAccessMapper.xml,否则该表文件对游客一律 403。
 */
@Mapper
public interface FileAccessMapper {

    int countPublicHits(@Param("path") String path);
}
