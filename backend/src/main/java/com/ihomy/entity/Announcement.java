package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 家庭公告/广告位实体(family_announcement):图片横幅 + 跳转链接,按排序值展示。
 * startDate/endDate 可空表示长期有效;enabled=0 为停用(仅家长可见)。
 */
@Data
@TableName("family_announcement")
public class Announcement {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private String title;
    private String imageUrl;
    private String linkUrl;
    private Integer sortOrder;
    private Integer enabled;
    private LocalDate startDate;
    private LocalDate endDate;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
