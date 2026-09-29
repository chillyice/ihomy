package com.ihomy.dto;

import lombok.Data;

/**
 * 观看状态上报(播放器进度 / 标记看过)。
 * played 留空表示只更新播放位置,不改变「看过」标记(避免续看中把已看过的片子翻回未看)。
 */
@Data
public class MediaProgressDTO {
    private Long positionTicks;
    private Boolean played;
}
