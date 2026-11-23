package com.jangveer.docintellect.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "document_metadata",
        indexes = @Index(
                name = "idx_document_metadata_status_uploaded_at",
                columnList = "status, uploaded_at"
        )
)
public class DocumentMetadata {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false, length = 100)
    private String contentType;

    @Column(nullable = false)
    private long fileSize;

    @Column(nullable = false, unique = true)
    private String storagePath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    protected DocumentMetadata() {
    }

    public DocumentMetadata(String fileName, String contentType, long fileSize, String storagePath) {
        this.id = UUID.randomUUID();
        this.fileName = fileName;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.storagePath = storagePath;
        this.status = DocumentStatus.UPLOADED;
    }

    @PrePersist
    void setUploadedAt() {
        if (uploadedAt == null) {
            uploadedAt = LocalDateTime.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public void setStatus(DocumentStatus status) {
        this.status = status;
    }
}
