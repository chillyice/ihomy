package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 歌单实体(content_music_playlist):is_background=1 表示该歌单是家庭背景歌单
 * (/music/background 免登录取用,首页/壁纸全局播放),一个家庭至多一个。
 */
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
