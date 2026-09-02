package com.facedb.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Iterator;
import java.util.UUID;

@Service
public class FileStorageService {

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    private static final List<String> ALLOWED_TYPES =
            List.of("image/jpeg", "image/jpg", "image/png", "image/webp");

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
            String imageFormat;
            try (ImageInputStream imageInput = ImageIO.createImageInputStream(file.getInputStream())) {
                if (imageInput == null) {
                    throw new IllegalArgumentException("Uploaded file is not a valid image");
                }
                Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
                if (!readers.hasNext()) {
                    throw new IllegalArgumentException("Uploaded file is not a valid image");
                }
                ImageReader reader = readers.next();
                try {
                    reader.setInput(imageInput, true, true);
                    imageFormat = reader.getFormatName().toLowerCase();
                } finally {
                    reader.dispose();
                }
            }
            if (!(imageFormat.equals("jpeg") || imageFormat.equals("jpg")
                    || imageFormat.equals("png") || imageFormat.equals("webp"))) {
                throw new IllegalArgumentException("Only JPEG, PNG, or WEBP images are allowed");
            }

            Path dir = getUploadDirectory();
            Files.createDirectories(dir);

            String filename = UUID.randomUUID() + extensionFor(imageFormat);
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

    public Path resolveStoredPath(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            throw new IllegalArgumentException("Stored file path cannot be empty");
        }
        Path uploadDirectory = getUploadDirectory();
        Path candidate = Paths.get(storedPath);
        Path resolved = candidate.isAbsolute()
            ? candidate.toAbsolutePath().normalize()
            : uploadDirectory.resolve(candidate.getFileName()).normalize();
        if (!resolved.startsWith(uploadDirectory)) {
            throw new IllegalArgumentException("Stored file path is outside the upload directory");
        }
        return resolved;
    }

    private String extensionFor(String imageFormat) {
        return switch (imageFormat) {
            case "png" -> ".png";
            case "webp" -> ".webp";
            default -> ".jpg";
        };
    }
}
