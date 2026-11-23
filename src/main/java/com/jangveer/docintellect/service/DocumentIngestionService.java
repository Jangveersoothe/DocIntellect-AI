package com.jangveer.docintellect.service;

import com.jangveer.docintellect.config.RagProperties;
import com.jangveer.docintellect.entity.DocumentMetadata;
import com.jangveer.docintellect.entity.DocumentStatus;
import com.jangveer.docintellect.exception.DocumentIndexingException;
import com.jangveer.docintellect.repository.DocumentMetadataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DocumentIngestionService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentIngestionService.class);

    private final LocalFileStorageService storageService;
    private final PdfTextExtractionService extractionService;
    private final TokenTextChunker chunker;
    private final VectorStore vectorStore;
    private final DocumentMetadataRepository metadataRepository;
    private final RagProperties ragProperties;
    private final DocumentOperationCoordinator operationCoordinator;

    public DocumentIngestionService(
            LocalFileStorageService storageService,
            PdfTextExtractionService extractionService,
            TokenTextChunker chunker,
            VectorStore vectorStore,
            DocumentMetadataRepository metadataRepository,
            RagProperties ragProperties,
            DocumentOperationCoordinator operationCoordinator
    ) {
        this.storageService = storageService;
        this.extractionService = extractionService;
        this.chunker = chunker;
        this.vectorStore = vectorStore;
        this.metadataRepository = metadataRepository;
        this.ragProperties = ragProperties;
        this.operationCoordinator = operationCoordinator;
    }

    @Async("documentIngestionExecutor")
    public void index(DocumentMetadata metadata) {
        operationCoordinator.execute(metadata.getId(), () -> indexDocument(metadata.getId()));
    }

    private void indexDocument(UUID documentId) {
        DocumentMetadata metadata = metadataRepository.findById(documentId).orElse(null);
        if (metadata == null) {
            return;
        }

        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                Path file = storageService.resolve(metadata.getStoragePath());
                List<PdfPageText> pages = extractionService.extractPages(file);
                List<TextChunk> textChunks = chunker.chunkPages(
                        metadata.getId(),
                        pages,
                        ragProperties.chunkSizeTokens(),
                        ragProperties.chunkOverlapTokens()
                );
                if (textChunks.isEmpty()) {
                    throw new IOException("The PDF contains no extractable text.");
                }

                List<Document> documents = textChunks.stream()
                        .map(chunk -> new Document(
                                chunk.text(),
                                Map.of(
                                        "documentId", metadata.getId().toString(),
                                        "fileName", metadata.getFileName(),
                                        "pageNumber", chunk.pageNumber(),
                                        "chunkIndex", chunk.chunkIndex(),
                                        "chunkId", chunk.id()
                                )
                        ))
                        .toList();
                vectorStore.add(documents);
                metadata.setStatus(DocumentStatus.INDEXED);
                metadataRepository.save(metadata);
                return;
            } catch (IOException | RuntimeException exception) {
                lastFailure = exception instanceof RuntimeException runtimeException
                        ? runtimeException
                        : new DocumentIndexingException("PDF extraction failed.", exception);
                logger.warn(
                        "Indexing attempt {}/3 failed for document {}",
                        attempt,
                        metadata.getId(),
                        exception
                );
                if (attempt < 3) {
                    try {
                        Thread.sleep(attempt * 1000L);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        metadata.setStatus(DocumentStatus.FAILED);
        metadataRepository.save(metadata);
        logger.error("Indexing failed permanently for document {}", metadata.getId(), lastFailure);
    }
}
