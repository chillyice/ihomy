package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("content_music_playlist_track")
public class ContentMusicPlaylistTrack {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long playlistId;
    private Long musicId;
    private Integer sortOrder;
    private LocalDateTime addedAt;
}
