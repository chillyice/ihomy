package com.ihomy.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 游戏系列自动归类:按名称切首个分隔段、去掉括号标注与结尾版本号;
 * 没有可切分部分不猜,手动值归一后优先。
 */
class GameSeriesTest {

    @Test
    void guessesFromNameSeparators() {
        assertThat(GameSeries.guess("宝可梦 绿宝石")).isEqualTo("宝可梦");
        assertThat(GameSeries.guess("宝可梦   火红")).isEqualTo("宝可梦");
        assertThat(GameSeries.guess("Pokemon - Emerald")).isEqualTo("Pokemon");
        assertThat(GameSeries.guess("Mario_Kart 8")).isEqualTo("Mario");
    }

    @Test
    void stripsBracketedEditionAndTrailingVersion() {
        assertThat(GameSeries.guess("宝可梦（中文版）")).isEqualTo("宝可梦");
        assertThat(GameSeries.guess("最终幻想7")).isEqualTo("最终幻想");
        assertThat(GameSeries.guess("FIFA 98")).isEqualTo("FIFA");
    }

    @Test
    void refusesToGuessWhenNothingCanBeSplit() {
        assertThat(GameSeries.guess("超级马里奥世界")).isNull();
        assertThat(GameSeries.guess("2048")).isNull();
        assertThat(GameSeries.guess("a b")).isNull();
        assertThat(GameSeries.guess(null)).isNull();
        assertThat(GameSeries.guess("   ")).isNull();
    }

    @Test
    void normalizesManualValue() {
        assertThat(GameSeries.normalize(" 宝可梦 ")).isEqualTo("宝可梦");
        assertThat(GameSeries.normalize("")).isNull();
        assertThat(GameSeries.normalize(null)).isNull();
        assertThat(GameSeries.normalize("x".repeat(GameSeries.MAX_LENGTH + 10)))
                .hasSize(GameSeries.MAX_LENGTH);
    }
}
