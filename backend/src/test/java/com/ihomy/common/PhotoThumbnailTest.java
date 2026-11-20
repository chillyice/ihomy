package com.ihomy.common;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class PhotoThumbnailTest {
    @ParameterizedTest
    @ValueSource(ints = {6, 8, 9})
    void portraitJpegMatchesExifDirectionAndInvalidOrientationUsesOriginal(int orientation) throws Exception {
        BufferedImage original = new BufferedImage(600, 300, BufferedImage.TYPE_INT_RGB);
        var g = original.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 300, 300);
        g.setColor(Color.BLUE);
        g.fillRect(300, 0, 300, 300);
        g.dispose();
        var out = new ByteArrayOutputStream();
        ImageIO.write(original, "jpg", out);
        byte[] jpeg = out.toByteArray();
        int afterJfif = 4 + ((jpeg[4] & 255) << 8) + (jpeg[5] & 255);
        byte[] app1 = { (byte) 0xff, (byte) 0xe1, 0, 34, 'E', 'x', 'i', 'f', 0, 0,
                'I', 'I', 42, 0, 8, 0, 0, 0, 1, 0, 0x12, 1, 3, 0, 1, 0, 0, 0,
                (byte) orientation, 0, 0, 0, 0, 0, 0, 0 };
        out.reset();
        out.write(jpeg, 0, afterJfif);
        out.write(app1);
        out.write(jpeg, afterJfif, jpeg.length - afterJfif);
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(out.toByteArray()))) {
            var preview = PhotoThumbnail.create(input);
            if (orientation == 9) {
                assertNull(preview);
                return;
            }
            assertNotNull(preview);
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(preview.bytes()));
            assertEquals(240, decoded.getWidth());
            assertEquals(480, decoded.getHeight());
            Color top = new Color(decoded.getRGB(100, 100));
            Color bottom = new Color(decoded.getRGB(100, 400));
            assertEquals(orientation == 6, top.getRed() > top.getBlue());
            assertEquals(orientation == 8, bottom.getRed() > bottom.getBlue());
        }
    }
}
