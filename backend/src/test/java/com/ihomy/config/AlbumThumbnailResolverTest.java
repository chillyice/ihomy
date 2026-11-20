package com.ihomy.config;

import com.ihomy.service.FileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AlbumThumbnailResolverTest {
    @TempDir Path root;
    private final AlbumThumbnailResolver resolver = new AlbumThumbnailResolver();
    private final MockHttpServletRequest request = new MockHttpServletRequest();

    private Resource resolve(String path) throws Exception {
        return resolver.resolveResource(request, path, List.of(new UrlResource(root.toUri())), null);
    }

    @Test
    void uploadCreatesUnambiguousVariantAndRemovalCleansIt() throws Exception {
        FileService files = new FileService();
        ReflectionTestUtils.setField(files, "uploadDir", root.toString());
        ReflectionTestUtils.setField(files, "urlPrefix", "/files");
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(600, 300, BufferedImage.TYPE_INT_RGB), "png", png);
        String url = files.upload(png.toByteArray(), "sample.png", "image/png", 2L, "family");
        String path = url.substring("/files/".length());
        assertEquals(600, ImageIO.read(resolve(path).getFile()).getWidth());
        request.setParameter("thumb", "1");
        Resource preview = resolve(path);
        assertEquals(480, ImageIO.read(preview.getFile()).getWidth());
        assertEquals(240, ImageIO.read(preview.getFile()).getHeight());
        files.deleteByUrl(url);
        assertFalse(preview.exists());
        assertNull(resolve(path));
    }

    @Test
    void olderFilesFallBackToOriginalEvenWhenAnUncorrectedThumbnailExists() throws Exception {
        String path = "pictures/family/old.png";
        Path original = root.resolve(path);
        Files.createDirectories(original.getParent());
        Files.writeString(original, "original");
        request.setParameter("thumb", "1");
        assertEquals(original, resolve(path).getFile().toPath());
        Path legacy = original.resolveSibling("old_thumb.jpg");
        Files.writeString(legacy, "legacy");
        assertEquals(original, resolve(path).getFile().toPath());
        Files.delete(original);
        assertNull(resolve(path));
    }

    @Test
    void pngThumbnailRetainsTransparencyAndUsesTheCorrectResourceType() throws Exception {
        FileService files = new FileService();
        ReflectionTestUtils.setField(files, "uploadDir", root.toString());
        ReflectionTestUtils.setField(files, "urlPrefix", "/files");
        BufferedImage image = new BufferedImage(600, 300, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xff0000ff);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        String url = files.upload(bytes.toByteArray(), "transparent.png", "image/png", 2L, "family");
        request.setParameter("thumb", "1");
        Resource preview = resolve(url.substring("/files/".length()));
        assertTrue(preview.getFilename().endsWith(".png"));
        BufferedImage decoded = ImageIO.read(preview.getFile());
        assertEquals(480, decoded.getWidth());
        assertEquals(0, decoded.getRGB(100, 100) >>> 24);
    }
}
