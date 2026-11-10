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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 系统分享收件桥(插件名 {@code IhomyShare}):把其他应用分享过来的文字/图片交给 WebView,
 * 由站内「分享到家庭」页面决定落到相册 / 愿望 / 日记,原生侧不猜测业务意图。
 *
 * <p>约定:
 * <ul>
 *   <li>图片一律**复制进应用缓存目录**再交给前端:分享方给的 content:// 授权只在本次意图内有效,
 *       复制后即使原应用退出也能上传;</li>
 *   <li>预览缩略图在原生侧降到 720px 再 base64 回传,避免把相机原图整份塞进 WebView 内存;</li>
 *   <li>最多 9 个文件、单个 ≤ 10MB(与后端单张上限一致)、合计 ≤ 90MB,超出的跳过并在返回里标明;</li>
 *   <li>{@code getPending} 只读不消费,前端确认落地后再调 {@code clearPending},失败可重试。</li>
 * </ul>
 */
@CapacitorPlugin(name = "IhomyShare")
public class ShareInboxPlugin extends Plugin {

    private static final int MAX_FILES = 9;
    /** 与后端单张图片上限一致(PhotoController.MAX_IMAGE_BYTES = 10MB) */
    private static final long MAX_FILE_BYTES = 10L * 1024L * 1024L;
    private static final long MAX_TOTAL_BYTES = 90L * 1024L * 1024L;
    private static final int MAX_TEXT_CHARS = 20000;
    private static final int THUMB_MAX_SIDE = 720;
    private static final int THUMB_QUALITY = 70;
    private static final String TAG = "IhomyShare";

    private static ShareInboxPlugin instance;

    /** 同一次分享重复调用 getPending 时直接复用上次结果(避免反复复制文件/解码缩略图) */
    private static long cachedFor = -1L;
    private static JSObject cachedPayload;

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

    /** 是否有待处理分享(前端启动时调用;只读,确认落地后调 clearPending) */
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
                ret.put("text", clip(pending.text));
                ret.put("subject", pending.subject == null ? "" : pending.subject);
                ret.put("receivedAt", pending.receivedAt);
                JSArray files = collectFiles(pending.uris);
                ret.put("files", files);
                ret.put("skipped", Math.max(0, pending.uris.size() - files.length()));
                cachedFor = pending.receivedAt;
                cachedPayload = ret;
                call.resolve(ret);
            } catch (Exception e) {
                call.reject("读取分享内容失败:" + e.getMessage(), e);
            }
        });
    }

    /** 内容已落到站内(或用户主动放弃),清掉待处理 */
    @PluginMethod
    public void clearPending(PluginCall call) {
        ShareIntake.clear();
        cachedFor = -1L;
        cachedPayload = null;
        call.resolve();
    }

    /**
     * 读取分享时复制到缓存目录的文件(base64)。只允许读我们自己复制的那批:
     * 路径必须落在 {@code <cache>/share-in/} 内,避免被当成任意文件读取口子。
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
                File dir = new File(getContext().getCacheDir(), "share-in");
                File file = new File(path);
                if (!file.getCanonicalPath().startsWith(dir.getCanonicalPath() + File.separator) || !file.isFile()) {
                    call.reject("文件已失效,请重新分享");
                    return;
                }
                if (file.length() > MAX_FILE_BYTES) {
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

    // ---- 落地:复制 + 缩略图 ----

    private JSArray collectFiles(List<Uri> uris) {
        JSArray out = new JSArray();
        File dir = new File(getContext().getCacheDir(), "share-in");
        clearDir(dir);
        if (!dir.exists() && !dir.mkdirs()) return out;
        long total = 0L;
        for (int i = 0; i < uris.size() && out.length() < MAX_FILES; i++) {
            Uri uri = uris.get(i);
            try {
                String mime = resolveMime(uri);
                String name = displayName(uri, i, mime);
                File dest = new File(dir, System.currentTimeMillis() + "-" + i + "-" + name);
                long copied = copyToFile(uri, dest, MAX_FILE_BYTES, MAX_TOTAL_BYTES - total);
                if (copied <= 0) {
                    //noinspection ResultOfMethodCallIgnored
                    dest.delete();
                    Log.w(TAG, "skip share file: " + uri + " (copy failed or too large)");
                    continue;
                }
                total += copied;
                JSObject f = new JSObject();
                f.put("name", name);
                f.put("mime", mime);
                f.put("size", copied);
                f.put("path", dest.getAbsolutePath());
                String thumb = thumbDataUrl(dest, mime);
                if (thumb != null) f.put("thumb", thumb);
                out.put(f);
            } catch (Exception e) {
                // 单个文件失败不影响其余(分享方给的 URI 可能已失效)
                Log.w(TAG, "read share file failed: " + uri, e);
            }
        }
        return out;
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
        if (mime == null || !mime.startsWith("image/")) return null;
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
