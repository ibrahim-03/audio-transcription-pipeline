package com.example.transcription.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Stores uploaded audio files on the local filesystem.
 * In production this would be replaced by S3 / MinIO / Azure Blob.
 */
@Service
public class StorageService {

    private final Path rootLocation;

    public StorageService(@Value("${app.storage.location:./uploads}") String location) throws IOException {
        this.rootLocation = Paths.get(location).toAbsolutePath().normalize();
        Files.createDirectories(this.rootLocation);
    }

    public String store(MultipartFile file) throws IOException {
        String extension = getExtension(file.getOriginalFilename());
        String filename = UUID.randomUUID() + (extension.isEmpty() ? "" : "." + extension);
        Path destination = rootLocation.resolve(filename);
        file.transferTo(destination);
        return destination.toString();
    }

    public Path resolve(String storedPath) {
        return Paths.get(storedPath);
    }

    public void delete(String storedPath) {
        try {
            Files.deleteIfExists(Paths.get(storedPath));
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
