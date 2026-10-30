package com.ihomy.common;

import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CSV 流式读取:引号内逗号/换行、双引号转义、CRLF、空行跳过与行号定位。
 * 导入报错要能指到用户在 Excel 里看到的行,所以行号口径也要一起测。
 */
class CsvReaderTest {

    private static CsvReader reader(String text) {
        return new CsvReader(new StringReader(text));
    }

    @Test
    void readsSimpleRecords() throws Exception {
        try (CsvReader csv = reader("a,b\n1,2\n")) {
            assertThat(csv.next().cells()).containsExactly("a", "b");
            assertThat(csv.next().cells()).containsExactly("1", "2");
            assertThat(csv.next()).isNull();
        }
    }

    @Test
    void keepsCommasInsideQuotes() throws Exception {
        try (CsvReader csv = reader("a,\"b,c\",d\n")) {
            assertThat(csv.next().cells()).containsExactly("a", "b,c", "d");
        }
    }

    @Test
    void keepsNewlinesInsideQuotesAndCountsLines() throws Exception {
        try (CsvReader csv = reader("a,\"line1\nline2\",c\nx,y,z\n")) {
            CsvReader.Record first = csv.next();
            assertThat(first.cells()).containsExactly("a", "line1\nline2", "c");
            assertThat(first.startLine()).isEqualTo(1);
            CsvReader.Record second = csv.next();
            assertThat(second.cells()).containsExactly("x", "y", "z");
            // 引号内换行占了一行,第二条记录从第 3 行开始
            assertThat(second.startLine()).isEqualTo(3);
        }
    }

    @Test
    void unescapesDoubledQuotes() throws Exception {
        try (CsvReader csv = reader("\"a\"\"b\",c\n")) {
            assertThat(csv.next().cells()).containsExactly("a\"b", "c");
        }
    }

    @Test
    void handlesCrlfAndSkipsBlankLines() throws Exception {
        try (CsvReader csv = reader("a,b\r\n\r\n1,2\r\n")) {
            assertThat(csv.next().cells()).containsExactly("a", "b");
            CsvReader.Record second = csv.next();
            assertThat(second.cells()).containsExactly("1", "2");
            assertThat(second.startLine()).isEqualTo(3);
        }
    }

    @Test
    void missingTrailingCellsAreNotInvented() throws Exception {
        try (CsvReader csv = reader("a,b,c\n1\n")) {
            csv.next();
            assertThat(csv.next().cells()).containsExactly("1");
        }
    }

    @Test
    void emptyFileReturnsNull() throws Exception {
        try (CsvReader csv = reader("")) {
            assertThat(csv.next()).isNull();
        }
    }
}
