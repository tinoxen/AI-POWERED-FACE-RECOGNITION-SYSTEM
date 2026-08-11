package com.facedb.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Service
public class FileStorageService {

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    private static final List<String> ALLOWED_TYPES =
            List.of("image/jpeg", "image/png", "image/webp");

    private static final long MAX_SIZE_BYTES = 5L * 1024 * 1024; // 5MB

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file provided");
        }
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("Only JPEG, PNG, or WEBP images are allowed");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException("File exceeds 5MB limit");
        }

        try {
            Path dir = Paths.get(uploadDir);
            Files.createDirectories(dir);

            String filename = UUID.randomUUID() + extensionFor(file.getContentType());
            Path target = dir.resolve(filename).normalize();
            if (!target.startsWith(dir)) {
                throw new IllegalArgumentException("Invalid file path");
            }

            // Preserve the validated source image. Re-encoding each upload
            // as WebP depends on an optional ImageIO codec and can make a
            // valid record submission fail before it reaches the database.
            Files.copy(file.getInputStream(), target);
            return target.toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file", e);
        }
    }

    public Path getUploadDirectory() {
        return Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }
}
