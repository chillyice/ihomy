package com.ihomy.filter;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 响应体截断捕获包装器:写出时旁路复制前 cap 字节,边写边透传不缓冲整包
 * (GB 级文件下载/流式视频零额外内存),供 AccessLogFilter 记录响应 code/message。
 */
public class CaptureResponseWrapper extends HttpServletResponseWrapper {

    private final int cap;
    private final ByteArrayOutputStream buf = new ByteArrayOutputStream();
    private ServletOutputStream wrapped;

    public CaptureResponseWrapper(HttpServletResponse response, int cap) {
        super(response);
        this.cap = cap;
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (wrapped == null) {
            wrapped = new TeeServletOutputStream(super.getOutputStream());
        }
        return wrapped;
    }

    /** 已捕获的响应体(UTF-8);二进制内容只有头部片段,调用方按 Content-Type 决定是否展示 */
    public String captured() {
        synchronized (buf) {
            return buf.toString(StandardCharsets.UTF_8).trim();
        }
    }

    private class TeeServletOutputStream extends ServletOutputStream {
        private final ServletOutputStream delegate;

        TeeServletOutputStream(ServletOutputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
            tee1();
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            delegate.write(b, off, len);
            synchronized (buf) {
                int remain = cap - buf.size();
                if (remain > 0) {
                    buf.write(b, off, Math.min(remain, len));
                }
            }
        }

        private void tee1() {
            synchronized (buf) {
                if (buf.size() < cap) {
                    buf.write(0);
                }
            }
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setWriteListener(WriteListener listener) {
            delegate.setWriteListener(listener);
        }
    }
}
