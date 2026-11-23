package com.jangveer.docintellect.service;

import com.jangveer.docintellect.dto.DocumentResponse;
import com.jangveer.docintellect.entity.DocumentStatus;
import com.jangveer.docintellect.repository.DocumentMetadataRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class DocumentMetadataTools {

    private final DocumentMetadataRepository repository;

    public DocumentMetadataTools(DocumentMetadataRepository repository) {
        this.repository = repository;
    }

    @Tool(description = "Find up to ten most recently uploaded documents by status.")
    public List<DocumentResponse> findRecentDocumentsByStatus(
            @ToolParam(description = "Document status: UPLOADED, INDEXED, or FAILED") String status
    ) {
        DocumentStatus documentStatus = DocumentStatus.valueOf(status.toUpperCase(Locale.ROOT));
        return repository.findTop10ByStatusOrderByUploadedAtDesc(documentStatus)
                .stream()
                .map(DocumentResponse::from)
                .toList();
    }
}
