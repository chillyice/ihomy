package com.ihomy.filter;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 请求体截断捕获包装器:控制器读流的同时旁路复制前 cap 字节,
 * 超出上限只透传不再复制(500MB 视频上传也只占 cap 字节内存)。
 * 供 AccessLogFilter 记录接口入参使用。
 */
public class CaptureRequestWrapper extends HttpServletRequestWrapper {

    private final int cap;
    private final ByteArrayOutputStream buf = new ByteArrayOutputStream();
    private ServletInputStream wrapped;

    public CaptureRequestWrapper(HttpServletRequest request, int cap) {
        super(request);
        this.cap = cap;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        if (wrapped == null) {
            wrapped = new TeeServletInputStream(super.getInputStream());
        }
        return wrapped;
    }

    /** 已捕获的请求体(UTF-8);控制器未读流(如 multipart)时为空串 */
    public String captured() {
        synchronized (buf) {
            return buf.toString(StandardCharsets.UTF_8).trim();
        }
    }

    private class TeeServletInputStream extends ServletInputStream {
        private final ServletInputStream delegate;

        TeeServletInputStream(ServletInputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b >= 0) {
                tee(new byte[]{(byte) b});
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = delegate.read(b, off, len);
            if (n > 0) {
                tee(java.util.Arrays.copyOfRange(b, off, off + n));
            }
            return n;
        }

        private void tee(byte[] bytes) {
            synchronized (buf) {
                int remain = cap - buf.size();
                if (remain > 0) {
                    buf.write(bytes, 0, Math.min(remain, bytes.length));
                }
            }
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
            delegate.setReadListener(listener);
        }
    }
}
