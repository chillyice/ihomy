package com.ihomy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 咔哒卡带发布请求(匿名):内容只按 JSON 数据处理,不做任何求值/渲染。
 * 服务端会独立做体积、字段、危险等级与法务关键词检查,客户端机审结果只作提示。
 */
@Data
public class KadaSnippetSubmitRequest {

    @NotBlank(message = "名称不能为空")
    @Size(max = 60, message = "名称最多 60 个字")
    private String name;

    @Size(max = 500, message = "说明最多 500 个字")
    private String description;

    @Size(max = 40, message = "作者最多 40 个字")
    private String author;

    @Size(max = 8, message = "标签最多 8 个")
    private List<@Size(max = 20, message = "每个标签最多 20 个字") String> tags;

    @NotBlank(message = "卡带内容不能为空")
    @Size(max = 300_000, message = "卡带内容太大")
    private String content;
}
