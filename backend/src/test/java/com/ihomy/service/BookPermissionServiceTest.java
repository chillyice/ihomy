package com.ihomy.service;

import com.ihomy.common.DictConst;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 账本档位的纯逻辑:等级排序与字典归一是权限判定的唯一依据,
 * 不依赖库(服务实例只用到 rank/normalize,不触 mapper)。
 */
class BookPermissionServiceTest {

    private final BookPermissionService service = new BookPermissionService(null, null, null);

    @Test
    void rankOrdersLevels() {
        assertThat(service.canRead(DictConst.BOOK_PERM_NONE)).isFalse();
        assertThat(service.canRead(DictConst.BOOK_PERM_READ)).isTrue();
        assertThat(service.canRead(DictConst.BOOK_PERM_WRITE)).isTrue();
        assertThat(service.canRead(DictConst.BOOK_PERM_MANAGE)).isTrue();

        assertThat(service.canWrite(DictConst.BOOK_PERM_READ)).isFalse();
        assertThat(service.canWrite(DictConst.BOOK_PERM_WRITE)).isTrue();
        assertThat(service.canWrite(DictConst.BOOK_PERM_MANAGE)).isTrue();

        assertThat(service.canManage(DictConst.BOOK_PERM_WRITE)).isFalse();
        assertThat(service.canManage(DictConst.BOOK_PERM_MANAGE)).isTrue();
    }

    @Test
    void unknownOrEmptyLevelFallsBackToWrite() {
        assertThat(DictConst.bookPermLevel(null)).isEqualTo(DictConst.BOOK_PERM_WRITE);
        assertThat(DictConst.bookPermLevel(" ")).isEqualTo(DictConst.BOOK_PERM_WRITE);
        assertThat(DictConst.bookPermLevel("MANAGE")).isEqualTo(DictConst.BOOK_PERM_MANAGE);
        assertThat(DictConst.bookPermLevel(" read ")).isEqualTo(DictConst.BOOK_PERM_READ);
        assertThat(DictConst.bookPermLevel("NONE")).isEqualTo(DictConst.BOOK_PERM_NONE);
    }

    @Test
    void onlyKnownLevelsAreValidForConfig() {
        assertThat(DictConst.isBookPermLevel("NONE")).isTrue();
        assertThat(DictConst.isBookPermLevel("READ")).isTrue();
        assertThat(DictConst.isBookPermLevel("WRITE")).isTrue();
        assertThat(DictConst.isBookPermLevel("MANAGE")).isTrue();
        assertThat(DictConst.isBookPermLevel("ADMIN")).isFalse();
        assertThat(DictConst.isBookPermLevel(null)).isFalse();
    }
}
