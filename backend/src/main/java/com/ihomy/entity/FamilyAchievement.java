package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 成就解锁记录(family_achievement):只存已解锁行,UNIQUE(user_id, code) 兼作并发兜底。
 * 目录本身在 common/AchievementCatalog;score 为解锁时的成就分快照。
 */
@Data
@TableName("family_achievement")
public class FamilyAchievement {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long familyId;
    private String code;
    /** 解锁时的成就分快照(目录调整不回写历史) */
    private Integer score;
    private LocalDateTime unlockedAt;
}
