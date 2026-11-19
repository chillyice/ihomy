package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 健康报表临时分享(family_health_share):分享出去的是一份冻结快照,
 * 不是实时数据通道——他人只能看到这一份报告,7 天到期或本人删除即消失。
 * 无 deleted 列,删除一律物理删(MP 的 delete 即物理删)。
 */
@Data
@TableName("family_health_share")
public class FamilyHealthShare {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private Long ownerUserId;
    private String token;
    /** JSON 数组:本报告包含的卡片 id */
    private String cards;
    /** 冻结的报表数据(JSON):读取端直接返回,不再查业务表 */
    private String snapshot;
    private Integer includeSensitive;
    private LocalDate anchorDate;
    private LocalDateTime expireAt;
    private Integer viewCount;
    private LocalDateTime lastViewedAt;
    private LocalDateTime createdAt;
}
