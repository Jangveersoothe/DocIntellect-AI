package com.jangveer.docintellect.dto;

import com.jangveer.docintellect.entity.DocumentMetadata;
import com.jangveer.docintellect.entity.DocumentStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        String fileName,
        String contentType,
        long fileSize,
        DocumentStatus status,
        LocalDateTime uploadedAt
) {
    public static DocumentResponse from(DocumentMetadata metadata) {
        return new DocumentResponse(
                metadata.getId(),
                metadata.getFileName(),
                metadata.getContentType(),
                metadata.getFileSize(),
                metadata.getStatus(),
                metadata.getUploadedAt()
        );
    }
}
