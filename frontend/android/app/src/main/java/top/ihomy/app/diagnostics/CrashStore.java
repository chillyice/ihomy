package top.ihomy.app.diagnostics;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 诊断落盘:原生崩溃写 {@code files/diagnostics/crash-<ts>.txt},前端错误写
 * {@code files/diagnostics/front-errors.txt}。
 *
 * <p>只在本地留存,由用户在设置页手动导出——不上传任何内容(计划 §5.3)。
 * 崩溃文件单份 ≤64KB、滚动保留 5 份;前端错误环形保留 200 条。
 * <p>崩溃处理器写完后**必须转调原 handler**,绝不吞掉崩溃。
 */
public final class CrashStore {

    private static final String TAG = "IhomyDiagnostics";
    private static final String DIR_NAME = "diagnostics";
    private static final String FRONT_FILE = "front-errors.txt";
    private static final int MAX_CRASH_FILES = 5;
    private static final int MAX_CRASH_BYTES = 64 * 1024;
    private static final int MAX_READ_BYTES = 512 * 1024;
    private static final int MAX_FRONT_LINES = 200;
    private static final int MAX_FRONT_LINE_CHARS = 1000;

    private static final Object LOCK = new Object();
    private static volatile boolean installed = false;

    private CrashStore() {
    }

    /** 安装全局崩溃处理器(只装一次;写盘后再交给系统,进程照常崩溃) */
    public static void install(Context context) {
        if (installed) return;
        installed = true;
        Context app = appContext(context);
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                writeCrash(app, thread, throwable);
            } catch (Throwable writeError) {
                Log.w(TAG, "crash dump failed", writeError);
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable);
            } else {
                // 极少数定制 ROM 没有默认处理器:写完自己结束进程,不让崩溃被悄悄吞掉
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(10);
            }
        });
    }

    /** 崩溃文件(按时间从新到旧);目录不存在时返回空数组 */
    public static File[] crashFiles(Context context) {
        File[] files = diagnosticsDir(context).listFiles((dir, name) -> name.matches("crash-\\d+\\.txt"));
        if (files == null || files.length == 0) return new File[0];
        Arrays.sort(files, Comparator.comparing(File::getName).reversed());
        return files;
    }

    /** 前端错误条数(UI 只显示数量,不读正文) */
    public static int frontErrorCount(Context context) {
        synchronized (LOCK) {
            File file = new File(diagnosticsDir(context), FRONT_FILE);
            if (!file.isFile()) return 0;
            try {
                return readLines(file).size();
            } catch (Exception e) {
                Log.w(TAG, "read front errors failed", e);
                return 0;
            }
        }
    }

    /** 追加前端错误(滚动保留最近 200 条);每行加时间戳并压成单行 */
    public static int appendFrontError(Context context, List<String> lines) {
        if (lines == null || lines.isEmpty()) return 0;
        synchronized (LOCK) {
            try {
                File file = new File(diagnosticsDir(context), FRONT_FILE);
                List<String> all = readLines(file);
                String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
                for (String raw : lines) {
                    String text = raw == null ? "" : raw.replace('\r', ' ').replace('\n', ' ');
                    if (text.length() > MAX_FRONT_LINE_CHARS) text = text.substring(0, MAX_FRONT_LINE_CHARS);
                    all.add(stamp + " " + text);
                }
                while (all.size() > MAX_FRONT_LINES) all.remove(0);
                writeText(file, joinLines(all));
                return lines.size();
            } catch (Exception e) {
                Log.w(TAG, "append front error failed", e);
                return 0;
            }
        }
    }

    /** 读诊断文件;调用方必须先校验 {@link #allowed(String)},这里再挡一次目录穿越 */
    public static String read(Context context, String name) throws IOException {
        if (!allowed(name)) throw new IOException("not allowed");
        File file = new File(diagnosticsDir(context), name);
        if (!file.isFile()) return "";
        return readText(file);
    }

    /** 清空全部诊断文件,返回删除份数 */
    public static int clear(Context context) {
        synchronized (LOCK) {
            int removed = 0;
            File[] files = diagnosticsDir(context).listFiles();
            if (files == null) return 0;
            for (File file : files) {
                if (file.isFile() && file.delete()) removed++;
            }
            return removed;
        }
    }

    /** 只允许固定前端错误文件与 crash-<数字>.txt,挡掉 ../ 与任意路径 */
    public static boolean allowed(String name) {
        if (name == null || name.isEmpty()) return false;
        if (FRONT_FILE.equals(name)) return true;
        return name.matches("crash-\\d+\\.txt");
    }

    private static void writeCrash(Context context, Thread thread, Throwable throwable) throws IOException {
        synchronized (LOCK) {
            StringBuilder sb = new StringBuilder();
            sb.append("time: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date())).append('\n');
            sb.append("model: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
            sb.append("android: ").append(Build.VERSION.RELEASE).append(" (sdk ").append(Build.VERSION.SDK_INT).append(")\n");
            sb.append("app: ").append(versionName(context)).append('\n');
            sb.append("thread: ").append(thread == null ? "?" : thread.getName()).append('\n');
            sb.append("---\n");
            sb.append(Log.getStackTraceString(throwable));
            File file = new File(diagnosticsDir(context), "crash-" + System.currentTimeMillis() + ".txt");
            writeText(file, sb.toString(), MAX_CRASH_BYTES);
            pruneOldCrashes(context);
        }
    }

    private static String versionName(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            long code = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? info.getLongVersionCode() : info.versionCode;
            return info.versionName + " (" + code + ")";
        } catch (Throwable ignored) {
            return "unknown";
        }
    }

    private static void pruneOldCrashes(Context context) {
        File[] files = crashFiles(context);
        for (int i = MAX_CRASH_FILES; i < files.length; i++) {
            if (!files[i].delete()) Log.w(TAG, "delete old crash failed: " + files[i].getName());
        }
    }

    private static File diagnosticsDir(Context context) {
        File dir = new File(appContext(context).getFilesDir(), DIR_NAME);
        if (!dir.isDirectory() && !dir.mkdirs()) {
            Log.w(TAG, "diagnostics dir unavailable");
        }
        return dir;
    }

    private static Context appContext(Context context) {
        Context app = context.getApplicationContext();
        return app == null ? context : app;
    }

    private static List<String> readLines(File file) throws IOException {
        List<String> lines = new ArrayList<>();
        if (!file.isFile()) return lines;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) lines.add(line);
        }
        return lines;
    }

    private static String readText(File file) throws IOException {
        byte[] all;
        try (FileInputStream in = new FileInputStream(file)) {
            int size = (int) Math.min(file.length(), MAX_READ_BYTES);
            all = new byte[size];
            int read = 0;
            while (read < size) {
                int n = in.read(all, read, size - read);
                if (n < 0) break;
                read += n;
            }
            if (read < size) all = Arrays.copyOf(all, read);
        }
        return new String(all, StandardCharsets.UTF_8);
    }

    private static void writeText(File file, String text) throws IOException {
        writeText(file, text, Integer.MAX_VALUE);
    }

    /** 按字节上限截断写入(不把半个 UTF-8 多字节字符留在文件尾) */
    private static void writeText(File file, String text, int maxBytes) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maxBytes) {
            int end = maxBytes;
            while (end > 0 && (bytes[end] & 0xC0) == 0x80) end--;
            bytes = Arrays.copyOf(bytes, end);
        }
        try (FileOutputStream out = new FileOutputStream(file, false)) {
            out.write(bytes);
        }
    }

    private static String joinLines(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (String line : lines) sb.append(line).append('\n');
        return sb.toString();
    }
}
