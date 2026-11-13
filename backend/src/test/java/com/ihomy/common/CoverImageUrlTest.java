package com.ihomy.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 家庭封面:空串归一为 NULL、换图/清除时才删旧文件 */
class CoverImageUrlTest {

    @Test
    void normalize_trimsAndTurnsBlankIntoNull() {
        assertEquals("/files/202611/abc.jpg", CoverImageUrl.normalize("  /files/202611/abc.jpg  "));
        assertNull(CoverImageUrl.normalize(""));
        assertNull(CoverImageUrl.normalize("   "));
        assertNull(CoverImageUrl.normalize(null));
    }

    @Test
    void obsoleteFile_onlyWhenCoverActuallyChanged() {
        assertEquals("/files/202610/old.jpg", CoverImageUrl.obsoleteFile("/files/202610/old.jpg", "/files/202611/new.jpg"));
        assertEquals("/files/202610/old.jpg", CoverImageUrl.obsoleteFile("/files/202610/old.jpg", null));
        assertNull(CoverImageUrl.obsoleteFile("/files/202610/old.jpg", "/files/202610/old.jpg"));
        assertNull(CoverImageUrl.obsoleteFile(null, "/files/202611/new.jpg"));
        assertNull(CoverImageUrl.obsoleteFile("", "/files/202611/new.jpg"));
        assertNull(CoverImageUrl.obsoleteFile("  ", null));
    }
}
