package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 家庭共享歌单曲目
 */
@Data
@TableName("family_music")
public class FamilyMusic {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private String url;
    private String title;
    private Long addedBy;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
