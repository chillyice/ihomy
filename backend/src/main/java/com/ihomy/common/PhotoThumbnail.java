package com.ihomy.common;

import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Thumbnail pixels match the browser's EXIF orientation. Preserve alpha in a PNG when present. */
public final class PhotoThumbnail {
    private static final int MAX_SIDE = 480;
    private PhotoThumbnail() {}

    public record Encoded(byte[] bytes, boolean png) {}

    /** Unsupported, animated, malformed or already small images keep their original representation. */
    public static Encoded create(ImageInputStream input) throws IOException {
        if (input == null) return null;
        var readers = ImageIO.getImageReaders(input);
        if (!readers.hasNext()) return null;
        ImageReader reader = readers.next();
        try {
            String format = reader.getFormatName();
            if (!"jpeg".equalsIgnoreCase(format) && !"png".equalsIgnoreCase(format)) return null;
            reader.setInput(input);
            int width = reader.getWidth(0), height = reader.getHeight(0);
            int longest = Math.max(width, height);
            if (longest <= MAX_SIDE) return null;
            IIOMetadata metadata = reader.getImageMetadata(0);
            int orientation = orientation(metadata.getAsTree(metadata.getNativeMetadataFormatName()));
            if (orientation < 1 || orientation > 8) return null;
            int sample = Math.max(1, longest / MAX_SIDE);
            var param = reader.getDefaultReadParam();
            param.setSourceSubsampling(sample, sample, 0, 0);
            BufferedImage src = reader.read(0, param);
            int w = Math.max(1, (int) Math.round((double) width * MAX_SIDE / longest));
            int h = Math.max(1, (int) Math.round((double) height * MAX_SIDE / longest));
            boolean alpha = src.getColorModel().hasAlpha();
            BufferedImage dst = new BufferedImage(orientation >= 5 ? h : w, orientation >= 5 ? w : h,
                    alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
            Graphics2D g = dst.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.transform(switch (orientation) {
                    case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0);
                    case 3 -> new AffineTransform(-1, 0, 0, -1, w, h);
                    case 4 -> new AffineTransform(1, 0, 0, -1, 0, h);
                    case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
                    case 6 -> new AffineTransform(0, 1, -1, 0, h, 0);
                    case 7 -> new AffineTransform(0, -1, -1, 0, h, w);
                    case 8 -> new AffineTransform(0, -1, 1, 0, 0, w);
                    default -> new AffineTransform();
                });
                g.drawImage(src, 0, 0, w, h, null);
            } finally {
                g.dispose();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            return ImageIO.write(dst, alpha ? "png" : "jpg", out) ? new Encoded(out.toByteArray(), alpha) : null;
        } finally {
            reader.dispose();
        }
    }

    private static int orientation(Node node) {
        if (node instanceof IIOMetadataNode m) {
            String type = m.getAttribute("type");
            if ("acTL".equals(type)) return 0; // let the browser animate an APNG
            if (m.getUserObject() instanceof byte[] bytes) {
                if ("eXIf".equals(type)) return readIfd(bytes, 0);
                if ("225".equals(m.getAttribute("MarkerTag")) && bytes.length >= 6
                        && bytes[0] == 'E' && bytes[1] == 'x' && bytes[2] == 'i' && bytes[3] == 'f'
                        && bytes[4] == 0 && bytes[5] == 0) return readIfd(bytes, 6);
            }
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            int result = orientation(child);
            if (result != 1) return result;
        }
        return 1;
    }

    /** TIFF IFD0 tag 0x0112 is a single SHORT, as in the frontend JPEG image tool. */
    private static int readIfd(byte[] bytes, int start) {
        ByteBuffer data = ByteBuffer.wrap(bytes, start, bytes.length - start).slice();
        if (data.limit() < 8) return 0;
        short order = data.getShort(0);
        if (order != 0x4949 && order != 0x4d4d) return 0;
        data.order(order == 0x4949 ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        if (data.getShort(2) != 42) return 0;
        int ifd = data.getInt(4);
        if (ifd < 8 || ifd > data.limit() - 2) return 0;
        int count = Short.toUnsignedInt(data.getShort(ifd));
        if (count > (data.limit() - ifd - 2) / 12) return 0;
        for (int i = 0; i < count; i++) {
            int offset = ifd + 2 + i * 12;
            if (data.getShort(offset) == 0x0112) {
                if (data.getShort(offset + 2) != 3 || data.getInt(offset + 4) != 1) return 0;
                return Short.toUnsignedInt(data.getShort(offset + 8));
            }
        }
        return 1;
    }
}
