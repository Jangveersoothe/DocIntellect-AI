package com.jangveer.docintellect.repository;

import com.jangveer.docintellect.entity.DocumentMetadata;
import com.jangveer.docintellect.entity.DocumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentMetadataRepository extends JpaRepository<DocumentMetadata, UUID> {

    List<DocumentMetadata> findTop10ByUserIdAndStatusOrderByUploadedAtDesc(UUID userId, DocumentStatus status);

    List<DocumentMetadata> findAllByUserIdOrderByUploadedAtDesc(UUID userId);

    List<DocumentMetadata> findAllByUserIdIsNull();

    Optional<DocumentMetadata> findByIdAndUserId(UUID id, UUID userId);
}
