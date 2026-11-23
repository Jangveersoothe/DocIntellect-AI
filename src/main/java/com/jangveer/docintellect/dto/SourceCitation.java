package com.jangveer.docintellect.dto;

import org.springframework.ai.document.Document;

import java.util.UUID;

public record SourceCitation(
        UUID documentId,
        String fileName,
        int pageNumber,
        String chunkId,
        String excerpt
) {
    public static SourceCitation from(Document document) {
        Object rawDocumentId = document.getMetadata().get("documentId");
        Object rawPageNumber = document.getMetadata().get("pageNumber");
        Object rawChunkId = document.getMetadata().get("chunkId");
        Object rawFileName = document.getMetadata().get("fileName");
        if (!(rawDocumentId instanceof String documentId)
                || !(rawPageNumber instanceof Number pageNumber)
                || !(rawChunkId instanceof String chunkId)
                || !(rawFileName instanceof String fileName)) {
            throw new IllegalStateException("Retrieved document is missing citation metadata.");
        }

        String text = document.getText();
        String excerpt = text.length() <= 300 ? text : text.substring(0, 300) + "...";
        return new SourceCitation(
                UUID.fromString(documentId),
                fileName,
                pageNumber.intValue(),
                chunkId,
                excerpt
        );
    }
}
