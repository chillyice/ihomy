package com.ihomy.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 小游戏分类归一:大小写/空白容错,白名单外的值一律落 OTHER,顺序与字典/前端一致。
 */
class GameCategoryTest {

    @Test
    void keepsKnownCategory() {
        assertThat(GameCategory.normalize("PUZZLE")).isEqualTo(GameCategory.PUZZLE);
        assertThat(GameCategory.normalize(" simulation ")).isEqualTo(GameCategory.SIMULATION);
    }

    @Test
    void unknownOrEmptyFallsBackToOther() {
        assertThat(GameCategory.normalize(null)).isEqualTo(GameCategory.OTHER);
        assertThat(GameCategory.normalize("   ")).isEqualTo(GameCategory.OTHER);
        assertThat(GameCategory.normalize("SHOOTER")).isEqualTo(GameCategory.OTHER);
    }

    @Test
    void allCategoriesAreUniqueAndEndWithOther() {
        assertThat(GameCategory.ALL).doesNotHaveDuplicates();
        assertThat(GameCategory.ALL).hasSize(9);
        assertThat(GameCategory.ALL.get(GameCategory.ALL.size() - 1)).isEqualTo(GameCategory.OTHER);
        assertThat(GameCategory.ALL).allSatisfy(c -> assertThat(c).isEqualTo(c.toUpperCase()));
    }
}
