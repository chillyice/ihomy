package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("content_music_playlist")
public class ContentMusicPlaylist {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private String name;
    private String coverUrl;
    private Integer trackCount;
    private Integer isBackground;
    private Long createdBy;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createdAt;
}
