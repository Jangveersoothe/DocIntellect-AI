package com.jangveer.docintellect.service;

import com.jangveer.docintellect.config.StorageProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class LocalFileStorageService {

    private final Path uploadDirectory;

    public LocalFileStorageService(StorageProperties storageProperties) throws IOException {
        this.uploadDirectory = storageProperties.uploadPath();
        Files.createDirectories(uploadDirectory);
    }

    public String store(MultipartFile file) throws IOException {
        String storageName = UUID.randomUUID() + ".pdf";
        Path destination = uploadDirectory.resolve(storageName).normalize();
        if (!destination.getParent().equals(uploadDirectory)) {
            throw new IOException("Resolved upload path is outside the configured upload directory.");
        }
        try (var input = file.getInputStream()) {
            Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            try {
                Files.deleteIfExists(destination);
            } catch (IOException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }
        return storageName;
    }

    public Path resolve(String storagePath) {
        Path resolved = uploadDirectory.resolve(storagePath).normalize();
        if (!resolved.getParent().equals(uploadDirectory)) {
            throw new IllegalArgumentException("Invalid stored document path.");
        }
        return resolved;
    }

    public void delete(String storagePath) throws IOException {
        Files.deleteIfExists(resolve(storagePath));
    }
}
