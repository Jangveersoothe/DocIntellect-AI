package com.jangveer.docintellect.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TokenTextChunkerTest {

    private final TokenTextChunker chunker = new TokenTextChunker();

    @Test
    void createsChunksWithConfiguredTokenOverlap() {
        List<String> chunks = chunker.chunk("one two three four five", 3, 1);

        assertEquals(List.of("one two three", "three four five"), chunks);
    }

    @Test
    void returnsNoChunksForBlankText() {
        assertEquals(List.of(), chunker.chunk(" \n ", 4, 1));
    }

    @Test
    void rejectsOverlapThatWouldPreventProgress() {
        assertThrows(IllegalArgumentException.class, () -> chunker.chunk("words", 3, 3));
    }

    @Test
    void pageChunksKeepPageNumbersAndStableIds() {
        UUID documentId = UUID.randomUUID();
        List<PdfPageText> pages = List.of(
                new PdfPageText(2, "one two three four"),
                new PdfPageText(5, "five six")
        );

        List<TextChunk> chunks = chunker.chunkPages(documentId, pages, 3, 1);
        List<TextChunk> repeated = chunker.chunkPages(documentId, pages, 3, 1);

        assertEquals(List.of(2, 2, 5), chunks.stream().map(TextChunk::pageNumber).toList());
        assertEquals(chunks.stream().map(TextChunk::id).toList(), repeated.stream().map(TextChunk::id).toList());
    }
}
