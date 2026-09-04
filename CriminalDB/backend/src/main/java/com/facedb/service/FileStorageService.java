package com.facedb.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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
            byte[] imageBytes = file.getBytes();
            String imageFormat;
            BufferedImage image;
            try (InputStream imageStream = new ByteArrayInputStream(imageBytes);
                 ImageInputStream imageInput = ImageIO.createImageInputStream(imageStream)) {
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
            image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (!(imageFormat.equals("jpeg") || imageFormat.equals("jpg")
                    || imageFormat.equals("png") || imageFormat.equals("webp"))) {
                throw new IllegalArgumentException("Only JPEG, PNG, or WEBP images are allowed");
            }

            Path dir = getUploadDirectory();
            Files.createDirectories(dir);

            String filename = UUID.randomUUID() + extensionFor();
            Path target = dir.resolve(filename).normalize();
            if (!target.startsWith(dir)) {
                throw new IllegalArgumentException("Invalid file path");
            }

            if (image == null || !ImageIO.write(image, "webp", target.toFile())) {
                throw new IllegalArgumentException("Uploaded image could not be converted to WebP");
            }
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

    private String extensionFor() { return ".webp"; }
}
