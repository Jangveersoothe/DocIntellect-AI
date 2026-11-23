package com.jangveer.docintellect.dto;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SourceCitationTest {

    @Test
    void mapsRetrievedChunkToPageCitationAndBoundsExcerpt() {
        UUID documentId = UUID.randomUUID();
        String text = "a".repeat(350);
        Document document = new Document(
                text,
                Map.of(
                        "documentId", documentId.toString(),
                        "fileName", "policy.pdf",
                        "pageNumber", 4,
                        "chunkId", "chunk-4-0"
                )
        );

        SourceCitation citation = SourceCitation.from(document);

        assertEquals(documentId, citation.documentId());
        assertEquals("policy.pdf", citation.fileName());
        assertEquals(4, citation.pageNumber());
        assertEquals("chunk-4-0", citation.chunkId());
        assertEquals(303, citation.excerpt().length());
    }
}
