package com.ihomy.dto;

import com.ihomy.entity.Photo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 自动备份单张照片的结果:skipped=true 表示这张内容已存在(命中内容哈希),没有重复占盘。
 * 照片本体一并返回,客户端可以直接拿去展示,不必再查一次列表。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PhotoBackupResult {
    private boolean skipped;
    private Photo photo;

    public static PhotoBackupResult skipped(Photo existing) {
        return new PhotoBackupResult(true, existing);
    }

    public static PhotoBackupResult created(Photo photo) {
        return new PhotoBackupResult(false, photo);
    }
}
