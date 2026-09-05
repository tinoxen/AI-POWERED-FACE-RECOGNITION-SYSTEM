package com.facedb.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

@Service
public class CloudinaryStorageService {

    public static final String REFERENCE_PREFIX = "cloudinary:";

    private final Cloudinary cloudinary;

    public CloudinaryStorageService() {
        this("", "", "");
    }

    @Autowired
    public CloudinaryStorageService(
            @Value("${CLOUDINARY_CLOUD_NAME:}") String cloudName,
            @Value("${CLOUDINARY_API_KEY:}") String apiKey,
            @Value("${CLOUDINARY_API_SECRET:}") String apiSecret) {
        if (cloudName.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) {
            cloudinary = null;
        } else {
            cloudinary = new Cloudinary(ObjectUtils.asMap(
                    "cloud_name", cloudName,
                    "api_key", apiKey,
                    "api_secret", apiSecret,
                    "secure", true));
        }
    }

    public boolean isConfigured() {
        return cloudinary != null;
    }

    public String upload(Path file) {
        requireConfigured();
        try {
            Map<?, ?> result = cloudinary.uploader().upload(file.toFile(), ObjectUtils.asMap(
                    "folder", "criminaldb",
                    "resource_type", "image",
                    "format", "webp"));
            return REFERENCE_PREFIX + result.get("public_id");
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload image to Cloudinary", e);
        }
    }

    public InputStream openStream(String reference) {
        requireConfigured();
        String publicId = publicId(reference);
        try {
            String url = cloudinary.url().secure(true).resourceType("image").format("webp").generate(publicId);
            return new URL(url).openStream();
        } catch (IOException e) {
            throw new RuntimeException("Failed to download image from Cloudinary", e);
        }
    }

    public void delete(String reference) {
        requireConfigured();
        try {
            cloudinary.uploader().destroy(publicId(reference), ObjectUtils.asMap("resource_type", "image"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete image from Cloudinary", e);
        }
    }

    public boolean isReference(String value) {
        return value != null && value.startsWith(REFERENCE_PREFIX)
                && value.length() > REFERENCE_PREFIX.length();
    }

    private String publicId(String reference) {
        if (!isReference(reference)) {
            throw new IllegalArgumentException("Invalid Cloudinary image reference");
        }
        return reference.substring(REFERENCE_PREFIX.length());
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException("Cloudinary storage is not configured");
        }
    }
}