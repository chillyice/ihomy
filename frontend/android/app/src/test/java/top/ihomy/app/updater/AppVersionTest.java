package top.ihomy.app.updater;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 原生侧版本比较:必须与前端 {@code utils/appUpdate.js} 同口径(只认 V&lt;主&gt;.&lt;次&gt;),
 * 且主版本优先于次版本——错了会导致「该更的不更」或「更到更低版本」。
 */
public class AppVersionTest {

    @Test
    public void parseStandardForm() {
        assertEquals(11092, AppVersion.code("V11.92"));
        assertEquals(11092, AppVersion.code("  v11.92 "));
        assertEquals(1000, AppVersion.code("V1.0"));
    }

    @Test
    public void invalidVersionsAreRejected() {
        assertEquals(-1, AppVersion.code("V11"));
        assertEquals(-1, AppVersion.code("11.92"));
        assertEquals(-1, AppVersion.code("V11.92.1"));
        assertEquals(-1, AppVersion.code(""));
        assertEquals(-1, AppVersion.code(null));
    }

    @Test
    public void majorWinsOverMinor() {
        assertTrue(AppVersion.compare("V12.0", "V11.99") > 0);
        assertTrue(AppVersion.compare("V11.99", "V12.0") < 0);
    }

    @Test
    public void minorComparedWithinMajor() {
        assertTrue(AppVersion.compare("V11.92", "V11.93") < 0);
        assertEquals(0, AppVersion.compare("V11.92", "V11.92"));
        assertTrue(AppVersion.compare("V11.93", "V11.92") > 0);
    }

    @Test
    public void invalidSideMeansNoUpdate() {
        assertEquals(0, AppVersion.compare("V11.92", "abc"));
        assertEquals(0, AppVersion.compare("", "V11.92"));
    }
}
