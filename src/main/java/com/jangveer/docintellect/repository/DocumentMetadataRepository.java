package com.jangveer.docintellect.repository;

import com.jangveer.docintellect.entity.DocumentMetadata;
import com.jangveer.docintellect.entity.DocumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentMetadataRepository extends JpaRepository<DocumentMetadata, UUID> {

    List<DocumentMetadata> findTop10ByStatusOrderByUploadedAtDesc(DocumentStatus status);
}
