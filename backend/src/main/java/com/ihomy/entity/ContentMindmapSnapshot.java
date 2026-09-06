package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 脑图历史版本快照(content_mindmap_snapshot)。
 * 编辑器定期/手动存档,误删可回滚;每图保留最近 20 份,超出自动清理。
 */
@Data
@TableName("content_mindmap_snapshot")
public class ContentMindmapSnapshot {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long mindmapId;

    private Long familyId;

    private Long userId;

    private String title;

    private String data;

    /** AUTO 自动 / MANUAL 手动 */
    private String source;

    private LocalDateTime createdAt;
}
