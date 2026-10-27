package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 产品访问与下载日统计实体(report_product_stat):每个产品每天一行。
 * views 来自落地页打点(POST /public/page-view),downloads 来自安装包下载计数接口;
 * 与访问日志解耦(nginx 静态直出 /files 的请求不进后端日志,日志口径统计不到下载)。
 */
@Data
@TableName("report_product_stat")
public class ProductStat {
    @TableId(type = IdType.AUTO)
    private Long id;
    private LocalDate statDate;
    private String productCode;
    private Long views;
    private Long downloads;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
