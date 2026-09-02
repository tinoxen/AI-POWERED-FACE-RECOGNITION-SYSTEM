package com.facedb.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileStorageServiceTest {

    @TempDir
    Path tempDirectory;

    private FileStorageService service;

    @BeforeEach
    void setUp() {
        service = new FileStorageService();
        ReflectionTestUtils.setField(service, "uploadDir", tempDirectory.toString());
    }

    @Test
    void storesImageUsingDetectedFormatAndManagedDirectory() throws Exception {
        MockMultipartFile upload = new MockMultipartFile(
                "photo", "client-name.png", "image/png", pngBytes());

        String storedPath = service.store(upload);

        Path resolved = service.resolveStoredPath(storedPath);
        assertTrue(resolved.startsWith(tempDirectory.toAbsolutePath()));
        assertTrue(resolved.getFileName().toString().endsWith(".png"));
        assertTrue(java.nio.file.Files.exists(resolved));
    }

    @Test
    void rejectsFileWithImageMimeTypeButInvalidImageBytes() {
        MockMultipartFile upload = new MockMultipartFile(
                "photo", "fake.png", "image/png", "not an image".getBytes());

        assertThrows(IllegalArgumentException.class, () -> service.store(upload));
    }

    @Test
    void rejectsPathOutsideUploadDirectory() {
        assertThrows(IllegalArgumentException.class,
                () -> service.resolveStoredPath(tempDirectory.resolveSibling("outside.png").toString()));
    }

    private byte[] pngBytes() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "png", output));
        assertEquals("png", ImageIO.getImageReadersByFormatName("png").next().getFormatName());
        return output.toByteArray();
    }
}
