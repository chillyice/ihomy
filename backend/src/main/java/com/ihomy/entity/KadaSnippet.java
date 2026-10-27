package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 咔哒卡带社区发布(sys_kada_snippet):
 * 用户提交的卡带先落库等机器复检 + OPS 人工审核,通过后才写文件并进社区索引。
 *
 * <p>`contentJson` 不参与列表 JSON 序列化(一页 20 条、单条最多 256 KB),详情接口单独返回。
 */
@Data
@TableName("sys_kada_snippet")
public class KadaSnippet {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    private String author;
    /** 标签 JSON 数组字符串。 */
    private String tags;
    @JsonIgnore
    private String contentJson;
    private String contentSha256;
    /** 0 安全 / 1 低 / 2 中 / 3 高。 */
    private Integer dangerLevel;
    /** 机器发现 JSON 数组字符串。 */
    private String findingsJson;
    /** 法务关键词命中 JSON 数组字符串。 */
    private String legalFlagsJson;
    /** PENDING / APPROVED / REJECTED。 */
    private String status;
    private String submitterIp;
    private Long reviewerId;
    private String reviewNote;
    private LocalDateTime reviewedAt;
    private String publishedUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
