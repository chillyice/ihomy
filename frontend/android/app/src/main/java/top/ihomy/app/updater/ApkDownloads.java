package top.ihomy.app.updater;

import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.util.Locale;

/**
 * 安装包下载的共用内核:系统 {@link DownloadManager} 入队 / 进度查询 / 落盘定位 / 清理。
 *
 * <p>两条入口共用一份实现,避免把 54MB 队列逻辑写两遍:
 * <ul>
 *   <li>{@link UpdaterPlugin}:用户点「立即更新」的交互式下载;</li>
 *   <li>{@link AutoUpdateWorker}:闲时(设备空闲 + 非计费网络)静默下载。</li>
 * </ul>
 *
 * <p>落点是应用外部私有目录 {@code Android/data/<pkg>/files/updates/}(卸载随应用清理,无需存储权限);
 * 老版本留在缓存目录的包仍能被 {@link #find} 认出。任务 id 与文件名记在 SharedPreferences,
 * 壳进程被杀后靠它认领「下好了没」。
 */
public final class ApkDownloads {

    public static final String DIR = "updates";
    public static final String PREFS = "ihomy_updater";
    public static final String KEY_ID = "download_id";
    public static final String KEY_FILE = "download_file";
    public static final String MIME_APK = "application/vnd.android.package-archive";
    /** 安装包下限:真包 10MB 以上,低于这个数说明拿到的是错误页/半截文件 */
    public static final long MIN_BYTES = 1024L * 1024L;

    private static final String TAG = "IhomyUpdater";

    private ApkDownloads() {
    }

    /** 一次查询的结果;{@code id} 用来在下载完成广播里比对是不是我们入队的那个任务 */
    public static final class State {
        public long id;
        public String status = "NONE";
        public String file = "";
        public long bytes;
        public long total;
        public long size;
        public int reason;
    }

    public static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * 入队后台下载:先撤掉上一次任务并删同名旧包(同一时刻只留一个包),再交给系统下载器。
     * {@code silent = true} 时不显示下载通知(设备所有者静默安装路径),否则完成时留一条通知。
     */
    public static long enqueue(Context context, String url, String file, boolean silent) throws Exception {
        String name = safeName(file);
        if (!isHttpUrl(url) || name == null) throw new IllegalArgumentException("bad-request");
        DownloadManager dm = downloadManager(context);
        if (dm == null) throw new IllegalStateException("no-download-manager");
        SharedPreferences prefs = prefs(context);
        long previous = prefs.getLong(KEY_ID, -1L);
        if (previous > 0) {
            try {
                dm.remove(previous);
            } catch (Exception ignored) {
                // 已被系统清掉,忽略
            }
        }
        File target = find(context, name);
        if (target != null && target.isFile() && !target.delete()) {
            Log.w(TAG, "删不掉旧安装包:" + target.getName());
        }
        DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
        req.setTitle(name);
        req.setDescription(context.getString(top.ihomy.app.R.string.app_name));
        req.setMimeType(MIME_APK);
        req.setNotificationVisibility(silent
                ? DownloadManager.Request.VISIBILITY_HIDDEN
                : DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        req.setDestinationInExternalFilesDir(context, null, DIR + "/" + name);
        // 走非计费网络:4G 下半路会暂停等 Wi-Fi,不偷偷吃流量(与「闲时有网」同口径)
        req.setAllowedOverMetered(false);
        req.setAllowedOverRoaming(false);
        long id = dm.enqueue(req);
        prefs.edit().putLong(KEY_ID, id).putString(KEY_FILE, name).apply();
        return id;
    }

    /** 查当前(或指定)任务的进度与落盘体积;任务被系统清掉时 status = NONE,文件可能仍在 */
    public static State query(Context context) {
        SharedPreferences prefs = prefs(context);
        State st = new State();
        st.id = prefs.getLong(KEY_ID, -1L);
        st.file = safeName(prefs.getString(KEY_FILE, ""));
        if (st.file == null) st.file = "";
        File file = st.file.isEmpty() ? null : find(context, st.file);
        st.size = file != null && file.isFile() ? file.length() : 0L;
        DownloadManager dm = downloadManager(context);
        if (dm == null || st.id <= 0) return st;
        Cursor cursor = null;
        try {
            cursor = dm.query(new DownloadManager.Query().setFilterById(st.id));
            if (cursor == null || !cursor.moveToFirst()) return st;
            int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            st.status = statusName(status);
            st.bytes = Math.max(0L, cursor.getLong(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)));
            st.total = Math.max(0L, cursor.getLong(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)));
            if (status == DownloadManager.STATUS_FAILED) {
                st.reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
            }
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                File done = find(context, st.file);
                st.size = done != null && done.isFile() ? done.length() : 0L;
            }
        } catch (Exception e) {
            Log.w(TAG, "查询下载进度失败", e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return st;
    }

    /** 删掉除 {@code keep} 之外的安装包(两处落点都清),返回删除个数 */
    public static int cleanup(Context context, String keep) {
        String name = safeName(keep);
        int removed = 0;
        for (File dir : new File[] {
                new File(context.getExternalFilesDir(null), DIR),
                new File(context.getCacheDir(), DIR) }) {
            File[] files = dir.listFiles();
            if (files == null) continue;
            for (File f : files) {
                if (f.isFile() && !f.getName().equals(name) && f.delete()) removed++;
            }
        }
        return removed;
    }

    /** 解析成安全文件名:只接受纯 .apk 名字(挡掉 ../ 与子路径),非法返回 null */
    public static String safeName(String name) {
        if (name == null) return null;
        String trimmed = name.trim();
        if (trimmed.isEmpty()) return null;
        if (trimmed.contains("/") || trimmed.contains("\\") || trimmed.contains("..")) return null;
        if (!trimmed.toLowerCase(Locale.ROOT).endsWith(".apk")) return null;
        return trimmed;
    }

    /** 安装包位置:新落点(外部私有目录)优先,老包可能还在缓存目录 */
    public static File find(Context context, String name) {
        String safe = safeName(name);
        if (safe == null) return null;
        File external = new File(new File(context.getExternalFilesDir(null), DIR), safe);
        if (external.isFile()) return external;
        File cached = new File(new File(context.getCacheDir(), DIR), safe);
        if (cached.isFile()) return cached;
        return null;
    }

    /** 下载目标路径(无论是否已存在),入队前删旧包用 */
    public static File target(Context context, String name) {
        String safe = safeName(name);
        if (safe == null) return null;
        return new File(new File(context.getExternalFilesDir(null), DIR), safe);
    }

    public static boolean isHttpUrl(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.startsWith("https://") || lower.startsWith("http://");
    }

    public static boolean active(String status) {
        return "PENDING".equals(status) || "RUNNING".equals(status) || "PAUSED".equals(status);
    }

    private static DownloadManager downloadManager(Context context) {
        return (DownloadManager) context.getApplicationContext().getSystemService(Context.DOWNLOAD_SERVICE);
    }

    private static String statusName(int status) {
        switch (status) {
            case DownloadManager.STATUS_PENDING:
                return "PENDING";
            case DownloadManager.STATUS_RUNNING:
                return "RUNNING";
            case DownloadManager.STATUS_PAUSED:
                return "PAUSED";
            case DownloadManager.STATUS_SUCCESSFUL:
                return "SUCCESSFUL";
            case DownloadManager.STATUS_FAILED:
                return "FAILED";
            default:
                return "UNKNOWN";
        }
    }
}
