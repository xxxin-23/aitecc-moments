package com.aitecc.moments.service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 图片落盘。
 *
 * <p>MVP 存本地磁盘，由 Spring 直接映射成 {@code /uploads/**} 静态资源。
 * 将来要换 OSS/COS 只需替换本类的 {@link #save} 实现，库里存的相对 URL 不用改。
 */
@Service
public class FileStorage {

    private static final long MAX_BYTES = 10L * 1024 * 1024; // 10MB
    private static final List<String> ALLOWED_TYPES =
            List.of("image/jpeg", "image/png", "image/gif", "image/webp", "image/heic");

    private final Path root;

    public FileStorage(@Value("${moments.upload-dir}") String uploadDir) {
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    /** 存一张图，返回可直接访问的相对 URL（如 {@code /uploads/xxx.jpg}）。 */
    public String save(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("图片为空");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BizException("单张图片不能超过 10MB：" + file.getOriginalFilename());
        }

        String contentType = file.getContentType();
        String ext = extensionOf(file.getOriginalFilename(), contentType);
        if (!ALLOWED_TYPES.contains(contentType) && ext.isBlank()) {
            throw new BizException("只支持 jpg / png / gif / webp / heic 图片");
        }

        try {
            // 不仅信 Content-Type，还实际解码一遍，挡掉「改了后缀的文本文件」。
            // 唯一的例外是 iPhone 默认的 heic/heif —— Java 自带解码器读不了，
            // 但那确实是照片，不能因为解不开就把同学拦在门外，只能放行存盘。
            if (!isHeic(ext)) {
                BufferedImage image = ImageIO.read(file.getInputStream());
                if (image == null) {
                    throw new BizException("文件不是有效图片：" + file.getOriginalFilename());
                }
            }

            Files.createDirectories(root);
            String name = UUID.randomUUID().toString().replace("-", "").substring(0, 16) + ext;
            Path target = root.resolve(name);
            try (var in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return "/uploads/" + name;
        } catch (IOException e) {
            throw new BizException("图片保存失败：" + e.getMessage());
        }
    }

    private static boolean isHeic(String ext) {
        return ".heic".equals(ext) || ".heif".equals(ext);
    }

    /** ContentType 不可信时，用原始文件名后缀兜底。 */
    private String extensionOf(String originalName, String contentType) {
        if (contentType != null) {
            String lower = contentType.toLowerCase(Locale.ROOT);
            if (lower.contains("jpeg")) return ".jpg";
            if (lower.contains("png")) return ".png";
            if (lower.contains("gif")) return ".gif";
            if (lower.contains("webp")) return ".webp";
            if (lower.contains("heic")) return ".heic";
        }
        if (originalName != null && originalName.contains(".")) {
            String suffix = originalName.substring(originalName.lastIndexOf('.'));
            if (suffix.length() <= 5) {
                return suffix.toLowerCase(Locale.ROOT);
            }
        }
        return "";
    }
}
