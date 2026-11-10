package top.ihomy.app.diagnostics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 崩溃落盘链路(计划 §5.3)的设备内验证:真的抛一个未捕获异常,看文件是否按口径落盘。
 *
 * <p>要点:先装一个「哑」默认处理器再调 {@link CrashStore#install(Context)},
 * CrashStore 写完后转调它,进程因此不会真的退出——否则测试进程一崩,断言就没机会跑。
 */
@RunWith(AndroidJUnit4.class)
public class CrashStoreTest {

    /** 与 CrashStore 内部口径一致 */
    private static final int MAX_CRASH_BYTES = 64 * 1024;
    private static final int MAX_CRASH_FILES = 5;
    /** CrashStore.install 只生效一次:处理器只能在一开始挂,后面重复 set 会把它的链断掉 */
    private static boolean handlerReady = false;

    private Context ctx() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    /** 装好处理器(仅一次)并清空历史 */
    private Context prepare() {
        Context context = ctx();
        if (!handlerReady) {
            Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
                // 故意什么都不做:让 CrashStore 写盘后进程存活
            });
            CrashStore.install(context);
            handlerReady = true;
        }
        CrashStore.clear(context);
        return context;
    }

    /** 在独立线程里抛一个未捕获异常,等它跑完(不抛回本线程) */
    private void crashInThread(RuntimeException error) {
        Thread thread = new Thread(() -> {
            throw error;
        }, "ihomy-crash-test");
        thread.start();
        try {
            thread.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("wait crash thread interrupted");
        }
        assertFalse("崩溃线程应已结束", thread.isAlive());
    }

    @Test
    public void crashWritesReadableDiagnosticFile() throws Exception {
        Context context = prepare();

        crashInThread(new RuntimeException("ihomy e2e crash"));

        File[] files = CrashStore.crashFiles(context);
        assertEquals("应恰好落下一份崩溃", 1, files.length);
        assertTrue("文件名必须是 crash-<数字>.txt", files[0].getName().matches("crash-\\d+\\.txt"));

        String text = CrashStore.read(context, files[0].getName());
        assertTrue("缺机型行", text.contains("model: "));
        assertTrue("缺 Android 版本行", text.contains("android: "));
        assertTrue("缺 App 版本行", text.contains("app: V"));
        assertTrue("缺线程名", text.contains("thread: ihomy-crash-test"));
        assertTrue("缺堆栈", text.contains("java.lang.RuntimeException: ihomy e2e crash"));
        // 单份体积在口径内
        assertTrue("单份不得超过 64KB", files[0].length() <= MAX_CRASH_BYTES);
    }

    @Test
    public void hugeCrashIsTruncatedTo64KB() throws Exception {
        Context context = prepare();

        StringBuilder big = new StringBuilder(220 * 1024);
        for (int i = 0; i < 220 * 1024; i++) big.append('x');
        crashInThread(new RuntimeException(big.toString()));

        File[] files = CrashStore.crashFiles(context);
        assertEquals(1, files.length);
        assertEquals("超长堆栈必须截到 64KB", MAX_CRASH_BYTES, files[0].length());
    }

    @Test
    public void keepsOnlyFiveNewestCrashes() throws Exception {
        Context context = prepare();

        for (int i = 0; i < MAX_CRASH_FILES + 1; i++) {
            crashInThread(new RuntimeException("roll-" + i));
            // 文件名用毫秒时间戳,同一毫秒内会互相覆盖
            Thread.sleep(20);
        }

        File[] files = CrashStore.crashFiles(context);
        assertEquals("滚动只留 5 份", MAX_CRASH_FILES, files.length);
        // 目录里最新的那份是最后一次崩溃,且旧的 roll-0 已被删
        String newest = CrashStore.read(context, files[0].getName());
        assertTrue(newest.contains("roll-" + MAX_CRASH_FILES));
        for (File file : files) {
            String text = CrashStore.read(context, file.getName());
            assertFalse("最旧的一份应被清掉", text.contains("roll-0"));
        }
    }

    @Test
    public void frontErrorsKeepTwoHundredLines() throws Exception {
        Context context = prepare();

        List<String> batch = new ArrayList<>();
        for (int i = 0; i < 250; i++) batch.add("line-" + i);
        assertEquals(250, CrashStore.appendFrontError(context, batch));
        assertEquals("环形缓冲上限 200 条", 200, CrashStore.frontErrorCount(context));

        String text = CrashStore.read(context, "front-errors.txt");
        assertTrue("最后一条要在", text.contains("line-249"));
        assertFalse("第 49 条之前的应被挤出", text.contains("line-49\n"));
    }

    @Test
    public void readRejectsPathTraversal() {
        Context context = prepare();

        for (String name : new String[] { "../secrets.xml", "diagnostics/x", "/data/data/x", "crash-1.txt.bak" }) {
            assertFalse(name + " 不该被允许", CrashStore.allowed(name));
            try {
                CrashStore.read(context, name);
                fail(name + " 应被拒绝");
            } catch (IOException expected) {
                // 预期
            }
        }
        assertTrue(CrashStore.allowed("crash-1794300000000.txt"));
        assertTrue(CrashStore.allowed("front-errors.txt"));
    }
}
