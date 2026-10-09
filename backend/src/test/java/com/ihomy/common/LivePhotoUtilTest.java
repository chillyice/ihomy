package com.ihomy.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Live Photo 配对:同名基底识别 + 短片判定(苹果导出工作流的核心逻辑)。 */
class LivePhotoUtilTest {

    @Test
    void pairsStillAndMotionByBaseName() {
        assertThat(LivePhotoUtil.baseKey("IMG_1234.HEIC"))
                .isEqualTo(LivePhotoUtil.baseKey("IMG_1234.MOV"))
                .isEqualTo("img_1234");
    }

    @Test
    void stripsDirectoryAndLowercases() {
        assertThat(LivePhotoUtil.baseKey("C:\\fakepath\\IMG_0042.JPG")).isEqualTo("img_0042");
        assertThat(LivePhotoUtil.baseKey("a/b/c.DNG")).isEqualTo("c");
        assertThat(LivePhotoUtil.baseKey(null)).isEmpty();
        assertThat(LivePhotoUtil.baseKey("noext")).isEqualTo("noext");
    }

    @Test
    void detectsVideoByExtensionOrContentType() {
        assertThat(LivePhotoUtil.isVideo("IMG_1234.MOV", null)).isTrue();
        assertThat(LivePhotoUtil.isVideo("clip.m4v", null)).isTrue();
        assertThat(LivePhotoUtil.isVideo("x", "video/quicktime")).isTrue();
        assertThat(LivePhotoUtil.isVideo("IMG_1234.HEIC", "image/heic")).isFalse();
        assertThat(LivePhotoUtil.isVideo(null, null)).isFalse();
    }
}
