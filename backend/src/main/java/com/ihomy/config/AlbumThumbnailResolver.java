package com.ihomy.config;

import com.ihomy.service.FileService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.PathResourceResolver;
import org.springframework.web.servlet.resource.ResourceResolverChain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;

/** Negotiate the thumbnail on the already-authorized photo URL. */
final class AlbumThumbnailResolver extends PathResourceResolver {
    @Override
    protected Resource resolveResourceInternal(HttpServletRequest request, String path,
            List<? extends Resource> locations, ResourceResolverChain chain) {
        Resource original = super.resolveResourceInternal(request, path, locations, chain);
        if (original == null || request == null || !"1".equals(request.getParameter("thumb"))
                || !path.startsWith("pictures/") || !original.isFile()) return original;
        try {
            Path file = original.getFile().toPath();
            Path jpeg = FileService.albumThumbPath(file, false);
            if (Files.isSymbolicLink(jpeg.getParent())) return original;
            if (Files.isRegularFile(jpeg, LinkOption.NOFOLLOW_LINKS)) return new FileSystemResource(jpeg);
            Path png = FileService.albumThumbPath(file, true);
            if (Files.isRegularFile(png, LinkOption.NOFOLLOW_LINKS)) return new FileSystemResource(png);
        } catch (IOException ignored) {
            // Deleted or non-local file: leave normal resource handling in charge.
        }
        return original;
    }
}
