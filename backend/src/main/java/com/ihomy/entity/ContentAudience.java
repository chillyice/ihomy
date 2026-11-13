package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 内容分享受众(content_audience):一条内容分享给一个接收家庭 = 一行。
 * 分享时把圈展开成逐家庭行,列表命中只多一个等值条件;新家庭中途入圈看不到历史分享。
 */
@Data
@TableName("content_audience")
public class ContentAudience {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** photo / album / video / blog / diary / wish / book */
    private String contentType;
    private Long contentId;
    /** 内容所属家庭(校验 + 首页缓存失效用) */
    private Long ownerFamilyId;
    /** 接收方类型,当前只有 FAMILY */
    private String targetType;
    /** 接收家庭 id */
    private Long targetId;
    /** 由哪个圈展开(NULL=单独给某家);退出/解散圈时按此批量收回 */
    private Long sourceCircleId;
    private Long grantedBy;
    private LocalDateTime createdAt;
}
