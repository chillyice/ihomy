package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 歌单曲目关联实体(content_music_playlist_track):歌单↔曲目的多对多中间表,无逻辑删,
 * 曲目移出歌单即物理删除本行;sort_order 决定播放顺序。
 */
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
