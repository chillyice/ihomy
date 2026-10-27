package com.ihomy.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** 卡带审核请求:备注可空(驳回时服务端补默认文案)。 */
@Data
public class KadaSnippetReviewRequest {

    @Size(max = 500, message = "审核备注最多 500 个字")
    private String note;
}
