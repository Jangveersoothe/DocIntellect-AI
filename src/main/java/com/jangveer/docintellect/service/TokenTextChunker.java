package com.jangveer.docintellect.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Component
public class TokenTextChunker {

    public List<String> chunk(String text, int chunkSizeTokens, int overlapTokens) {
        if (chunkSizeTokens < 1 || overlapTokens < 0 || overlapTokens >= chunkSizeTokens) {
            throw new IllegalArgumentException("Chunk size must be positive and overlap must be smaller than chunk size.");
        }
        String normalized = text == null ? "" : text.strip();
        if (normalized.isEmpty()) {
            return List.of();
        }

        String[] tokens = normalized.split("\\s+");
        List<String> chunks = new ArrayList<>();
        int step = chunkSizeTokens - overlapTokens;
        for (int start = 0; start < tokens.length; start += step) {
            int end = Math.min(start + chunkSizeTokens, tokens.length);
            chunks.add(String.join(" ", Arrays.copyOfRange(tokens, start, end)));
            if (end == tokens.length) {
                break;
            }
        }
        return List.copyOf(chunks);
    }

    public List<TextChunk> chunkPages(
            UUID documentId,
            List<PdfPageText> pages,
            int chunkSizeTokens,
            int overlapTokens
    ) {
        if (chunkSizeTokens < 1 || overlapTokens < 0 || overlapTokens >= chunkSizeTokens) {
            throw new IllegalArgumentException("Chunk size must be positive and overlap must be smaller than chunk size.");
        }
        List<TextChunk> chunks = new ArrayList<>();
        for (PdfPageText page : pages) {
            List<String> pageChunks = chunk(page.text(), chunkSizeTokens, overlapTokens);
            for (int chunkIndex = 0; chunkIndex < pageChunks.size(); chunkIndex++) {
                String id = UUID.nameUUIDFromBytes(
                        (documentId + ":" + page.pageNumber() + ":" + chunkIndex)
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8)
                ).toString();
                chunks.add(new TextChunk(id, page.pageNumber(), chunkIndex, pageChunks.get(chunkIndex)));
            }
        }
        return List.copyOf(chunks);
    }
}
