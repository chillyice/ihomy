package top.ihomy.app.photo;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;
import android.webkit.MimeTypeMap;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 相册自动备份桥(插件名 {@code IhomyPhotoBackup}):按系统相册增量列出手机照片、
 * 复制到应用缓存并算出内容指纹,再由前端逐张上传到「手机备份」相册。
 *
 * <p>约定:
 * <ul>
 *   <li>只读系统相册,不申请写入/删除权限,也不改动用户原图;</li>
 *   <li>指纹(SHA-256)在复制时顺带算好,前端上传后由服务端按指纹去重,重复的不会再存一份;</li>
 *   <li>图片先落到应用缓存目录,前端用本地资源地址取回再上传,避免把整张原图 base64 塞进 WebView 内存;</li>
 *   <li>分页按 (DATE_ADDED, _ID) 双键推进,同一秒内的多张照片也不会漏或重复。</li>
 * </ul>
 *
 * <p>不做的事:不在后台自动跑(没有前台服务、没有 Worker),备份只在应用前台时由前端触发。
 */
@CapacitorPlugin(
        name = "IhomyPhotoBackup",
        permissions = {
            @Permission(alias = "images", strings = { Manifest.permission.READ_MEDIA_IMAGES }),
            @Permission(
                    alias = "imagesSelected",
                    strings = { Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED }),
            @Permission(alias = "imagesLegacy", strings = { Manifest.permission.READ_EXTERNAL_STORAGE })
        })
public class PhotoBackupPlugin extends Plugin {

    private static final String TAG = "IhomyPhotoBackup";
    /** 单张上限:超过的多为 RAW/连拍原图,跳过并交给用户手动处理,不拖垮手机与上传链路 */
    private static final long MAX_PHOTO_BYTES = 30L * 1024L * 1024L;
    private static final String CACHE_DIR = "photo-backup";

    private final ExecutorService io = Executors.newSingleThreadExecutor();

    /** Android 13+ 读图片用细分权限,12 及以下仍是存储权限 */
    private String imageAlias() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ? "images" : "imagesLegacy";
    }

    /** Android 14「仅允许访问选中的照片」:用户给了部分授权,能读到被选中的那些 */
    private boolean hasSelectedAccess() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                && getPermissionState("imagesSelected") == PermissionState.GRANTED;
    }

    /** 只要能读到照片(完整授权或部分授权)就算可读 */
    private boolean hasImageAccess() {
        return getPermissionState(imageAlias()) == PermissionState.GRANTED || hasSelectedAccess();
    }

    @PluginMethod
    public void status(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("permission", hasImageAccess() ? PermissionState.GRANTED.toString() : getPermissionState(imageAlias()).toString());
        ret.put("partial", hasSelectedAccess());
        ret.put("charging", isCharging());
        ret.put("wifi", isWifi());
        ret.put("metered", isMetered());
        ret.put("cacheBytes", cacheBytes());
        call.resolve(ret);
    }

    @PluginMethod
    public void checkPermission(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("state", hasImageAccess() ? PermissionState.GRANTED.toString() : getPermissionState(imageAlias()).toString());
        call.resolve(ret);
    }

    @PluginMethod
    public void requestPermission(PluginCall call) {
        if (hasImageAccess()) {
            JSObject ret = new JSObject();
            ret.put("state", PermissionState.GRANTED.toString());
            call.resolve(ret);
            return;
        }
        requestPermissionForAlias(imageAlias(), call, "permissionCallback");
    }

    @PermissionCallback
    private void permissionCallback(PluginCall call) {
        JSObject ret = new JSObject();
        // Android 14 部分授权弹窗(选了部分照片)不会把 images 置为 GRANTED,按实际可读性回报
        ret.put("state", hasImageAccess() ? PermissionState.GRANTED.toString() : getPermissionState(imageAlias()).toString());
        ret.put("partial", hasSelectedAccess());
        call.resolve(ret);
    }

    /** 待备份张数(只数拍摄/添加时间不早于给定时刻的照片),用于界面显示「还有多少」;excludeFolders 里的相册目录不数 */
    @PluginMethod
    public void count(PluginCall call) {
        if (!hasImageAccess()) {
            JSObject ret = new JSObject();
            ret.put("total", 0);
            ret.put("granted", false);
            call.resolve(ret);
            return;
        }
        long sinceMs = call.getLong("sinceMs", 0L);
        JSArray exclude = call.getArray("excludeFolders");
        io.execute(() -> {
            try {
                JSObject ret = new JSObject();
                ret.put("total", countSince(sinceMs, exclude));
                ret.put("granted", true);
                call.resolve(ret);
            } catch (Exception e) {
                Log.w(TAG, "count failed", e);
                call.reject("读取相册张数失败");
            }
        });
    }

    /**
     * 系统相册目录(按 MediaStore 桶分组):供用户选「哪些目录不同步」。
     * 只返回有照片的目录,计数为该目录下的图片张数。
     */
    @PluginMethod
    public void folders(PluginCall call) {
        if (!hasImageAccess()) {
            // 未授权不再静默返回空列表:让上层弹权限或提示,否则「没读到相册」无法区分原因
            call.reject("未获授权读取相册");
            return;
        }
        io.execute(() -> {
            try {
                JSObject ret = new JSObject();
                ret.put("folders", queryFolders());
                call.resolve(ret);
            } catch (Exception e) {
                Log.w(TAG, "folders failed", e);
                call.reject("读取相册目录失败");
            }
        });
    }

    /**
     * 增量列出一页照片(按添加时间升序)。
     * {@code afterDateAdded}/{@code afterId} 是上一次的游标,首次传 0 从 {@code sinceMs} 起。
     */
    @PluginMethod
    public void list(PluginCall call) {
        if (getPermissionState(imageAlias()) != PermissionState.GRANTED) {
            call.reject("permission");
            return;
        }
        long sinceMs = call.getLong("sinceMs", 0L);
        long afterDateAdded = call.getLong("afterDateAdded", 0L);
        long afterId = call.getLong("afterId", 0L);
        int limit = call.getInt("limit", 50);
        if (limit <= 0 || limit > 200) limit = 50;
        final int pageSize = limit;
        final JSArray exclude = call.getArray("excludeFolders");
        io.execute(() -> {
            try {
                JSArray items = new JSArray();
                boolean hasMore = queryPage(sinceMs, afterDateAdded, afterId, pageSize, exclude, items);
                JSObject ret = new JSObject();
                ret.put("items", items);
                ret.put("hasMore", hasMore);
                call.resolve(ret);
            } catch (Exception e) {
                Log.w(TAG, "list failed", e);
                call.reject("读取相册失败");
            }
        });
    }

    /**
     * 把一张照片复制进缓存并算内容指纹。返回的 path 供前端用本地资源地址取回;
     * 超过体积上限的返回 {@code tooLarge=true}(不抛异常,便于前端继续下一张)。
     */
    @PluginMethod
    public void prepare(PluginCall call) {
        if (getPermissionState(imageAlias()) != PermissionState.GRANTED) {
            call.reject("permission");
            return;
        }
        Long mediaId = call.getLong("mediaId");
        if (mediaId == null || mediaId <= 0) {
            call.reject("mediaId required");
            return;
        }
        long id = mediaId;
        io.execute(() -> {
            File dest = null;
            try {
                Uri uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                Meta meta = readMeta(uri);
                if (meta == null) {
                    call.reject("照片已不在相册里");
                    return;
                }
                JSObject ret = new JSObject();
                ret.put("mediaId", id);
                ret.put("name", meta.name);
                ret.put("mime", meta.mime);
                ret.put("takenAt", meta.takenAt);
                if (meta.size > MAX_PHOTO_BYTES) {
                    ret.put("tooLarge", true);
                    ret.put("size", meta.size);
                    call.resolve(ret);
                    return;
                }
                File dir = new File(getContext().getCacheDir(), CACHE_DIR);
                if (!dir.exists() && !dir.mkdirs()) {
                    call.reject("无法创建缓存目录");
                    return;
                }
                dest = new File(dir, id + "-" + System.currentTimeMillis() + extension(meta));
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                long copied = copy(uri, dest, digest);
                if (copied <= 0) {
                    //noinspection ResultOfMethodCallIgnored
                    dest.delete();
                    call.reject("读取照片失败");
                    return;
                }
                ret.put("tooLarge", false);
                ret.put("size", copied);
                ret.put("checksum", hex(digest.digest()));
                ret.put("path", dest.getAbsolutePath());
                call.resolve(ret);
            } catch (Exception e) {
                if (dest != null) {
                    //noinspection ResultOfMethodCallIgnored
                    dest.delete();
                }
                Log.w(TAG, "prepare failed: " + id, e);
                call.reject("准备上传失败");
            }
        });
    }

    /** 删掉单张缓存文件(上传完成或失败后调用);只允许删缓存目录里的东西 */
    @PluginMethod
    public void drop(PluginCall call) {
        String path = call.getString("path");
        if (path == null || path.isEmpty()) {
            call.resolve();
            return;
        }
        io.execute(() -> {
            try {
                File dir = new File(getContext().getCacheDir(), CACHE_DIR);
                File file = new File(path);
                String base = dir.getCanonicalPath() + File.separator;
                if (file.getCanonicalPath().startsWith(base)) {
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                }
                call.resolve();
            } catch (Exception e) {
                Log.w(TAG, "drop failed", e);
                call.resolve();
            }
        });
    }

    /**
     * 兜底读取缓存文件(base64):个别机型 WebView 取不到本地资源地址时用。
     * 只允许读缓存目录里我们刚写的那一份,且上限 12MB。
     */
    @PluginMethod
    public void readBase64(PluginCall call) {
        String path = call.getString("path");
        if (path == null || path.isEmpty()) {
            call.reject("path required");
            return;
        }
        io.execute(() -> {
            try {
                File dir = new File(getContext().getCacheDir(), CACHE_DIR);
                File file = new File(path);
                if (!file.getCanonicalPath().startsWith(dir.getCanonicalPath() + File.separator) || !file.isFile()) {
                    call.reject("照片已失效,请重试");
                    return;
                }
                long length = file.length();
                if (length > 12L * 1024L * 1024L) {
                    call.reject("单张照片过大");
                    return;
                }
                byte[] bytes = new byte[(int) length];
                try (InputStream in = new java.io.FileInputStream(file)) {
                    int read = 0;
                    while (read < bytes.length) {
                        int n = in.read(bytes, read, bytes.length - read);
                        if (n < 0) break;
                        read += n;
                    }
                }
                JSObject ret = new JSObject();
                ret.put("data", android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP));
                ret.put("size", bytes.length);
                call.resolve(ret);
            } catch (Exception e) {
                Log.w(TAG, "readBase64 failed", e);
                call.reject("读取照片失败");
            }
        });
    }

    /** 清空备份用的缓存图片(退出备份界面或备份结束时调用) */
    @PluginMethod
    public void cleanup(PluginCall call) {
        io.execute(() -> {
            int removed = 0;
            File[] files = new File(getContext().getCacheDir(), CACHE_DIR).listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile() && f.delete()) removed++;
                }
            }
            JSObject ret = new JSObject();
            ret.put("removed", removed);
            call.resolve(ret);
        });
    }

    // ---- 相册读取 ----

    private long countSince(long sinceMs, JSArray exclude) {
        long sinceSec = Math.max(0L, sinceMs / 1000L);
        Uri uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String[] projection = { MediaStore.Images.Media._ID };
        StringBuilder selection = new StringBuilder(MediaStore.Images.Media.DATE_ADDED + " >= ?");
        java.util.List<String> args = new java.util.ArrayList<>();
        args.add(String.valueOf(sinceSec));
        appendExcludeBuckets(selection, args, exclude);
        long total = 0L;
        try (Cursor c = getContext().getContentResolver()
                .query(uri, projection, selection.toString(), args.toArray(new String[0]), null)) {
            if (c != null) total = c.getCount();
        } catch (Exception e) {
            Log.w(TAG, "countSince failed", e);
        }
        return total;
    }

    /** 系统相册目录列表(MediaStore 桶):id 用桶 ID,名称取目录显示名,count 为图片张数 */
    private JSArray queryFolders() {
        Uri uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        };
        java.util.Map<Long, String> names = new java.util.LinkedHashMap<>();
        java.util.Map<Long, Integer> counts = new java.util.HashMap<>();
        String order = MediaStore.Images.Media.BUCKET_DISPLAY_NAME + " ASC, " + MediaStore.Images.Media._ID + " ASC";
        try (Cursor c = getContext().getContentResolver().query(uri, projection, null, null, order)) {
            if (c != null) {
                while (c.moveToNext()) {
                    long bucketId = c.getLong(0);
                    String name = c.getString(1);
                    if (name == null || name.trim().isEmpty()) name = "其他";
                    if (!names.containsKey(bucketId)) names.put(bucketId, name);
                    Integer old = counts.get(bucketId);
                    counts.put(bucketId, old == null ? 1 : old + 1);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "queryFolders failed", e);
        }
        JSArray arr = new JSArray();
        for (java.util.Map.Entry<Long, String> e : names.entrySet()) {
            try {
                JSObject item = new JSObject();
                item.put("id", String.valueOf(e.getKey()));
                item.put("name", e.getValue());
                Integer n = counts.get(e.getKey());
                item.put("count", n == null ? 0 : n);
                arr.put(item);
            } catch (Exception ignored) {
                // 单个目录拼装失败不影响其它目录
            }
        }
        return arr;
    }

    /**
     * 排除用户选中的相册目录(桶 ID):只接受纯数字,最多 200 个。
     * 桶为空的照片(极少见)仍然备份,避免因为怪数据整批漏备。
     */
    private void appendExcludeBuckets(StringBuilder selection, java.util.List<String> args, JSArray exclude) {
        if (exclude == null || exclude.length() == 0) return;
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (int i = 0; i < exclude.length() && ids.size() < 200; i++) {
            String id = exclude.optString(i, null);
            if (id != null && !id.isEmpty() && id.matches("\\d+")) ids.add(id);
        }
        if (ids.isEmpty()) return;
        selection.append(" AND (").append(MediaStore.Images.Media.BUCKET_ID).append(" IS NULL OR ")
                .append(MediaStore.Images.Media.BUCKET_ID).append(" NOT IN (")
                .append(String.join(",", java.util.Collections.nCopies(ids.size(), "?"))).append("))");
        args.addAll(ids);
    }

    /** 返回是否还有下一页(读到 pageSize 条就认为可能有,前端再探一页即可) */
    private boolean queryPage(long sinceMs, long afterDateAdded, long afterId, int pageSize, JSArray exclude, JSArray out) {
        long sinceSec = Math.max(0L, sinceMs / 1000L);
        long afterSec = Math.max(0L, afterDateAdded / 1000L);
        Uri uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_TAKEN
        };
        StringBuilder selection = new StringBuilder(MediaStore.Images.Media.DATE_ADDED + " >= ?");
        java.util.List<String> args = new java.util.ArrayList<>();
        args.add(String.valueOf(sinceSec));
        if (afterDateAdded > 0) {
            // 双键游标:同一秒里的多张照片按 _ID 继续往后,既不漏也不重
            selection.append(" AND (").append(MediaStore.Images.Media.DATE_ADDED).append(" > ? OR (")
                    .append(MediaStore.Images.Media.DATE_ADDED).append(" = ? AND ")
                    .append(MediaStore.Images.Media._ID).append(" > ?))");
            args.add(String.valueOf(afterSec));
            args.add(String.valueOf(afterSec));
            args.add(String.valueOf(afterId));
        }
        appendExcludeBuckets(selection, args, exclude);
        String order = MediaStore.Images.Media.DATE_ADDED + " ASC, " + MediaStore.Images.Media._ID + " ASC";
        int read = 0;
        try (Cursor c = getContext().getContentResolver()
                .query(uri, projection, selection.toString(), args.toArray(new String[0]), order)) {
            if (c == null) return false;
            while (c.moveToNext() && read < pageSize) {
                long id = c.getLong(0);
                String name = c.getString(1);
                String mime = c.getString(2);
                long size = c.getLong(3);
                long dateAdded = c.getLong(4) * 1000L;
                long takenAt = 0L;
                try {
                    takenAt = c.getLong(5);
                } catch (Exception ignored) {
                    // 个别机型 DATE_TAKEN 为空:按「没有拍摄时间」处理
                }
                JSObject item = new JSObject();
                item.put("mediaId", id);
                item.put("name", name == null ? "" : name);
                item.put("mime", mime == null ? "image/jpeg" : mime);
                item.put("size", size);
                item.put("dateAdded", dateAdded);
                item.put("takenAt", takenAt);
                out.put(item);
                read++;
            }
            return read >= pageSize;
        }
    }

    private static final class Meta {
        String name;
        String mime;
        long size;
        long takenAt;
    }

    private Meta readMeta(Uri uri) {
        String[] projection = {
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_TAKEN
        };
        try (Cursor c = getContext().getContentResolver().query(uri, projection, null, null, null)) {
            if (c == null || !c.moveToFirst()) return null;
            Meta m = new Meta();
            m.name = c.getString(0);
            m.mime = c.getString(1);
            m.size = c.getLong(2);
            try {
                m.takenAt = c.getLong(3);
            } catch (Exception ignored) {
                m.takenAt = 0L;
            }
            if (m.name == null || m.name.trim().isEmpty()) m.name = "photo";
            if (m.mime == null || !m.mime.contains("/")) m.mime = guessMime(m.name);
            return m;
        } catch (Exception e) {
            Log.w(TAG, "readMeta failed: " + uri, e);
            return null;
        }
    }

    private static String guessMime(String name) {
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            String ext = name.substring(dot + 1).toLowerCase();
            String guess = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            if (guess != null) return guess;
        }
        return "image/jpeg";
    }

    private static String extension(Meta meta) {
        int dot = meta.name.lastIndexOf('.');
        if (dot >= 0 && meta.name.length() - dot <= 6) {
            return meta.name.substring(dot).replaceAll("[^A-Za-z0-9.]", "");
        }
        String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(meta.mime);
        return ext == null ? ".jpg" : "." + ext;
    }

    /** 流式复制并同步更新指纹;超过上限立刻放弃(返回 -1) */
    private long copy(Uri uri, File dest, MessageDigest digest) throws Exception {
        ContentResolver resolver = getContext().getContentResolver();
        long total = 0L;
        try (InputStream in = resolver.openInputStream(uri);
             OutputStream out = new FileOutputStream(dest)) {
            if (in == null) return 0L;
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > MAX_PHOTO_BYTES) return -1L;
                digest.update(buf, 0, n);
                out.write(buf, 0, n);
            }
            out.flush();
        }
        return total;
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private long cacheBytes() {
        File[] files = new File(getContext().getCacheDir(), CACHE_DIR).listFiles();
        if (files == null) return 0L;
        long total = 0L;
        for (File f : files) total += f.length();
        return total;
    }

    // ---- 设备状态(备份开关要按这些判断) ----

    private boolean isCharging() {
        try {
            BatteryManager bm = (BatteryManager) getContext().getSystemService(Context.BATTERY_SERVICE);
            if (bm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                int status = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS);
                if (status == BatteryManager.BATTERY_STATUS_CHARGING
                        || status == BatteryManager.BATTERY_STATUS_FULL) {
                    return true;
                }
            }
            // 少数机型上面取到的状态不刷新:回退读一次电量广播
            Intent battery = getContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (battery == null) return false;
            int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            return status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isWifi() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            Network network = cm.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isMetered() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            return cm != null && cm.isActiveNetworkMetered();
        } catch (Exception e) {
            return true;
        }
    }
}
