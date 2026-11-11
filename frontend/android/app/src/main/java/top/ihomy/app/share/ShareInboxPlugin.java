package top.ihomy.app.share;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.util.Log;
import android.webkit.MimeTypeMap;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 系统收件桥(插件名 {@code IhomyShare}):把其他应用分享/打开过来的文字、图片、文档、视频交给 WebView,
 * 由站内「分享到家庭」页面决定落到相册 / 图书馆 / 放映厅,原生侧不猜测业务意图。
 *
 * <p>约定:
 * <ul>
 *   <li><b>图片</b>立即复制进应用缓存目录(分享方给的 content:// 授权只在本次意图内有效),
 *       并在原生侧降到 720px 生成 base64 缩略图;单张 ≤10MB(与后端一致),一次最多 9 张;</li>
 *   <li><b>文档 / 视频</b>只回元数据(name/mime/size),用户点「保存」时才 {@code prepare} 复制,
 *       避免「只是看一眼」也白拷几百 MB 进缓存;单文件 ≤500MB(与后端 multipart 上限一致);</li>
 *   <li>{@code getPending} 只读不消费,前端确认落地后再调 {@code clearPending},失败可重试;</li>
 *   <li>{@code readFile} 的 base64 通道保留为兜底(硬限 10MB),大文件走 {@code convertFileSrc} 流式读取。</li>
 * </ul>
 */
@CapacitorPlugin(name = "IhomyShare")
public class ShareInboxPlugin extends Plugin {

    /** 图片一次最多 9 张(与原分享口径一致) */
    private static final int MAX_IMAGE_FILES = 9;
    /** 与后端单张图片上限一致(PhotoController.MAX_IMAGE_BYTES = 10MB) */
    private static final long MAX_IMAGE_BYTES = 10L * 1024L * 1024L;
    private static final long MAX_IMAGE_TOTAL_BYTES = 90L * 1024L * 1024L;
    /** 文档/视频惰性复制上限,对齐 spring.servlet.multipart.max-file-size = 500MB */
    private static final long MAX_DOC_BYTES = 500L * 1024L * 1024L;
    private static final long MAX_DOC_TOTAL_BYTES = 500L * 1024L * 1024L;
    private static final int MAX_TEXT_CHARS = 20000;
    private static final int THUMB_MAX_SIDE = 720;
    private static final int THUMB_QUALITY = 70;
    private static final String DIR_NAME = "share-in";
    private static final String TAG = "IhomyShare";

    private static ShareInboxPlugin instance;

    /** 同一次分享重复调用 getPending 时直接复用上次结果(避免反复复制文件/解码缩略图) */
    private static long cachedFor = -1L;
    private static JSObject cachedPayload;
    /** 已 prepare 过的文件(index → 缓存路径);同一次收件内不重复复制 */
    private static final Map<Integer, String> preparedPaths = new HashMap<>();

    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @Override
    public void load() {
        instance = this;
    }

    /** 主线程调用(App 已在前台时再次收到分享):内容已接住,通知 JS 去取 */
    public static void captureAndNotify(Context context, Intent intent) {
        if (!ShareIntake.capture(intent)) return;
        ShareInboxPlugin p = instance;
        if (p != null) p.notifyListeners("shareReceived", new JSObject());
    }

    /** 是否有待处理收件(前端启动时调用;只读,确认落地后调 clearPending) */
    @PluginMethod
    public void getPending(PluginCall call) {
        io.execute(() -> {
            try {
                ShareIntake.Pending pending = ShareIntake.peek();
                if (pending == null) {
                    JSObject empty = new JSObject();
                    empty.put("has", false);
                    call.resolve(empty);
                    return;
                }
                if (pending.receivedAt == cachedFor && cachedPayload != null) {
                    call.resolve(cachedPayload);
                    return;
                }
                JSObject ret = new JSObject();
                ret.put("has", true);
                ret.put("mode", pending.mode == null ? ShareIntake.MODE_SHARE : pending.mode);
                ret.put("text", clip(pending.text));
                ret.put("subject", pending.subject == null ? "" : pending.subject);
                ret.put("receivedAt", pending.receivedAt);
                JSArray files = buildFiles(pending);
                ret.put("files", files);
                int skippedImages = skipCount.images;
                int skippedDocs = skipCount.docs;
                ret.put("skipped", skippedImages + skippedDocs);
                ret.put("skippedImages", skippedImages);
                ret.put("skippedDocs", skippedDocs);
                cachedFor = pending.receivedAt;
                cachedPayload = ret;
                call.resolve(ret);
            } catch (Exception e) {
                call.reject("读取分享内容失败:" + e.getMessage(), e);
            }
        });
    }

    /** 内容已落到站内(或用户主动放弃),清掉待处理与缓存文件 */
    @PluginMethod
    public void clearPending(PluginCall call) {
        ShareIntake.clear();
        cachedFor = -1L;
        cachedPayload = null;
        synchronized (preparedPaths) {
            preparedPaths.clear();
        }
        io.execute(() -> clearDir(shareDir()));
        call.resolve();
    }

    /**
     * 文档/视频惰性复制:用户点「保存」时才把 content:// 落进缓存目录,返回真实路径。
     * 图片也会走这里兜底(若前端发现没有 path,例如页面刷新后缓存被系统清过)。
     */
    @PluginMethod
    public void prepare(PluginCall call) {
        Integer index = call.getInt("index");
        if (index == null) {
            call.reject("index required");
            return;
        }
        io.execute(() -> {
            try {
                ShareIntake.Pending pending = ShareIntake.peek();
                if (pending == null || index < 0 || index >= pending.uris.size()) {
                    call.reject("文件已失效,请重新分享");
                    return;
                }
                synchronized (preparedPaths) {
                    String cached = preparedPaths.get(index);
                    if (cached != null) {
                        File cf = new File(cached);
                        if (cf.isFile() && cf.length() > 0) {
                            resolvePrepared(call, cf, resolveMime(pending.uris.get(index)));
                            return;
                        }
                        preparedPaths.remove(index);
                    }
                }
                Uri uri = pending.uris.get(index);
                String mime = resolveMime(uri);
                String name = displayName(uri, index, mime);
                File dir = shareDir();
                if (!dir.exists() && !dir.mkdirs()) {
                    call.reject("copy_failed");
                    return;
                }
                File dest = new File(dir, pending.receivedAt + "-" + index + "-" + name);
                if (dest.isFile() && dest.length() > 0) {
                    // 图片在 getPending 时已复制过,直接复用
                    rememberPrepared(index, dest);
                    resolvePrepared(call, dest, mime);
                    return;
                }
                long remain = MAX_DOC_TOTAL_BYTES - dirSize(dir);
                if (remain <= 0) {
                    call.reject("too_large");
                    return;
                }
                long copied = copyToFile(uri, dest, MAX_DOC_BYTES, remain);
                if (copied == -1L) {
                    //noinspection ResultOfMethodCallIgnored
                    dest.delete();
                    call.reject("too_large");
                    return;
                }
                if (copied <= 0) {
                    //noinspection ResultOfMethodCallIgnored
                    dest.delete();
                    call.reject("copy_failed");
                    return;
                }
                rememberPrepared(index, dest);
                resolvePrepared(call, dest, mime);
            } catch (Exception e) {
                call.reject("copy_failed", e);
            }
        });
    }

    /**
     * 读取分享时复制到缓存目录的文件(base64)。只允许读我们自己复制的那批:
     * 路径必须落在 {@code <cache>/share-in/} 内,避免被当成任意文件读取口子。
     * 只作兜底通道(硬限 10MB),大文件走 {@code prepare} + 本地资源地址流式读取。
     */
    @PluginMethod
    public void readFile(PluginCall call) {
        String path = call.getString("path");
        if (path == null) {
            call.reject("path required");
            return;
        }
        io.execute(() -> {
            try {
                File dir = shareDir();
                File file = new File(path);
                if (!file.getCanonicalPath().startsWith(dir.getCanonicalPath() + File.separator) || !file.isFile()) {
                    call.reject("文件已失效,请重新分享");
                    return;
                }
                if (file.length() > MAX_IMAGE_BYTES) {
                    call.reject("文件超过 10MB");
                    return;
                }
                byte[] bytes = new byte[(int) file.length()];
                try (InputStream in = new java.io.FileInputStream(file)) {
                    int read = 0;
                    while (read < bytes.length) {
                        int n = in.read(bytes, read, bytes.length - read);
                        if (n < 0) break;
                        read += n;
                    }
                }
                JSObject ret = new JSObject();
                ret.put("data", Base64.encodeToString(bytes, Base64.NO_WRAP));
                ret.put("name", file.getName());
                ret.put("size", bytes.length);
                call.resolve(ret);
            } catch (Exception e) {
                call.reject("读取分享文件失败:" + e.getMessage(), e);
            }
        });
    }

    // ---- 落地:图片立即复制 + 文档只回元数据 ----

    /** 一次 collect 的跳过计数(buildFiles 写完即用,单线程 io 串行不会有并发) */
    private final SkipCount skipCount = new SkipCount();

    private static final class SkipCount {
        int images;
        int docs;
    }

    private JSArray buildFiles(ShareIntake.Pending pending) {
        JSArray out = new JSArray();
        File dir = shareDir();
        clearDir(dir);
        synchronized (preparedPaths) {
            preparedPaths.clear();
        }
        skipCount.images = 0;
        skipCount.docs = 0;
        if (!dir.exists() && !dir.mkdirs()) return out;
        long imageTotal = 0L;
        int imageCount = 0;
        for (int i = 0; i < pending.uris.size(); i++) {
            Uri uri = pending.uris.get(i);
            try {
                String mime = resolveMime(uri);
                String name = displayName(uri, i, mime);
                if (isImage(mime)) {
                    if (imageCount >= MAX_IMAGE_FILES) {
                        skipCount.images++;
                        continue;
                    }
                    File dest = new File(dir, pending.receivedAt + "-" + i + "-" + name);
                    long copied = copyToFile(uri, dest, MAX_IMAGE_BYTES, MAX_IMAGE_TOTAL_BYTES - imageTotal);
                    if (copied <= 0) {
                        //noinspection ResultOfMethodCallIgnored
                        dest.delete();
                        Log.w(TAG, "skip share image: " + uri + " (copy failed or too large)");
                        skipCount.images++;
                        continue;
                    }
                    imageTotal += copied;
                    imageCount++;
                    JSObject f = new JSObject();
                    f.put("index", i);
                    f.put("kind", "image");
                    f.put("name", name);
                    f.put("mime", mime);
                    f.put("size", copied);
                    f.put("path", dest.getAbsolutePath());
                    String thumb = thumbDataUrl(dest, mime);
                    if (thumb != null) f.put("thumb", thumb);
                    out.put(f);
                } else {
                    long size = fileSize(uri);
                    if (size > MAX_DOC_BYTES) {
                        skipCount.docs++;
                        Log.w(TAG, "skip share doc: " + uri + " (too large)");
                        continue;
                    }
                    JSObject f = new JSObject();
                    f.put("index", i);
                    f.put("kind", "doc");
                    f.put("name", name);
                    f.put("mime", mime);
                    // 取不到大小给 -1,前端显示「—」,保存时仍按 500MB 上限校验
                    f.put("size", size);
                    out.put(f);
                }
            } catch (Exception e) {
                // 单个文件失败不影响其余(分享方给的 URI 可能已失效)
                Log.w(TAG, "read share file failed: " + uri, e);
            }
        }
        return out;
    }

    private void resolvePrepared(PluginCall call, File file, String mime) {
        JSObject ret = new JSObject();
        ret.put("path", file.getAbsolutePath());
        ret.put("size", file.length());
        ret.put("name", stripPrefix(file.getName()));
        ret.put("mime", mime);
        call.resolve(ret);
    }

    private void rememberPrepared(int index, File file) {
        synchronized (preparedPaths) {
            preparedPaths.put(index, file.getAbsolutePath());
        }
    }

    /** 缓存目录 {@code <cache>/share-in/} */
    private File shareDir() {
        return new File(getContext().getCacheDir(), DIR_NAME);
    }

    private static boolean isImage(String mime) {
        return mime != null && mime.startsWith("image/");
    }

    /** 文件名去掉 {@code <receivedAt>-<index>-} 前缀,回传给前端做展示/标题 */
    private static String stripPrefix(String name) {
        if (name == null) return "";
        int first = name.indexOf('-');
        if (first < 0) return name;
        int second = name.indexOf('-', first + 1);
        return second >= 0 ? name.substring(second + 1) : name;
    }

    /** 分享方不给 MIME 时按扩展名兜底(常见于 file:// 或自定义 provider) */
    private String resolveMime(Uri uri) {
        String mime = null;
        try {
            mime = getContext().getContentResolver().getType(uri);
        } catch (Exception ignored) {
            // 取不到就按扩展名猜
        }
        if (mime != null && mime.contains("/")) return mime;
        String ext = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
        if (ext == null || ext.isEmpty()) {
            String s = uri.getLastPathSegment();
            int dot = s == null ? -1 : s.lastIndexOf('.');
            ext = dot >= 0 ? s.substring(dot + 1) : null;
        }
        String guess = ext == null ? null : MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase());
        return guess == null ? "application/octet-stream" : guess;
    }

    /** 文档大小:取不到给 -1(不阻塞保存,前端仍按接口上限校验) */
    private long fileSize(Uri uri) {
        try (Cursor c = getContext().getContentResolver()
                .query(uri, new String[] { OpenableColumns.SIZE }, null, null, null)) {
            if (c != null && c.moveToFirst() && !c.isNull(0)) return c.getLong(0);
        } catch (Exception ignored) {
            // 部分 provider 不支持 SIZE 列
        }
        return -1L;
    }

    private long dirSize(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return 0L;
        long total = 0L;
        for (File f : files) total += f.length();
        return total;
    }

    private long copyToFile(Uri uri, File dest, long perFileLimit, long remainTotal) throws Exception {
        long limit = Math.min(perFileLimit, remainTotal);
        if (limit <= 0) return 0L;
        long total = 0L;
        try (InputStream in = getContext().getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(dest)) {
            if (in == null) return 0L;
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > limit) return -1L;
                out.write(buf, 0, n);
            }
            out.flush();
        }
        return total;
    }

    /** 缩略图 data URL(仅图片);大图先按 2 的幂降采样再按最长边缩放 */
    private String thumbDataUrl(File file, String mime) {
        if (!isImage(mime)) return null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            int longest = Math.max(bounds.outWidth, bounds.outHeight);
            if (longest <= 0) return null;

            BitmapFactory.Options opts = new BitmapFactory.Options();
            int sample = 1;
            while (longest / (sample * 2) >= THUMB_MAX_SIDE) sample *= 2;
            opts.inSampleSize = sample;
            Bitmap source = BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
            if (source == null) return null;
            int side = Math.max(source.getWidth(), source.getHeight());
            Bitmap scaled = source;
            if (side > THUMB_MAX_SIDE) {
                float ratio = (float) THUMB_MAX_SIDE / side;
                scaled = Bitmap.createScaledBitmap(
                        source,
                        Math.max(1, Math.round(source.getWidth() * ratio)),
                        Math.max(1, Math.round(source.getHeight() * ratio)),
                        true);
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            scaled.compress(Bitmap.CompressFormat.JPEG, THUMB_QUALITY, bos);
            if (scaled != source) scaled.recycle();
            source.recycle();
            return "data:image/jpeg;base64,"
                    + Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    private String displayName(Uri uri, int index, String mime) {
        String name = null;
        try (Cursor c = getContext()
                .getContentResolver()
                .query(uri, new String[] { OpenableColumns.DISPLAY_NAME }, null, null, null)) {
            if (c != null && c.moveToFirst()) name = c.getString(0);
        } catch (Exception ignored) {
            // 取不到文件名就用兜底名
        }
        if (name == null || name.trim().isEmpty()) {
            String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
            name = "share-" + index + (ext == null ? "" : "." + ext);
        }
        // 文件名参与本地路径拼接,去掉路径分隔符与控制字符
        return name.replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]", "_");
    }

    private static String clip(String text) {
        if (text == null) return "";
        String t = text.trim();
        return t.length() <= MAX_TEXT_CHARS ? t : t.substring(0, MAX_TEXT_CHARS);
    }

    private static void clearDir(File dir) {
        File[] old = dir.listFiles();
        if (old == null) return;
        for (File f : old) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }
}
