package com.jangveer.docintellect.service;

import com.jangveer.docintellect.config.DocumentProperties;
import com.jangveer.docintellect.dto.BatchDeleteItemResult;
import com.jangveer.docintellect.dto.BatchDeleteResponse;
import com.jangveer.docintellect.dto.BatchUploadItemResult;
import com.jangveer.docintellect.dto.BatchUploadResponse;
import com.jangveer.docintellect.dto.DocumentResponse;
import com.jangveer.docintellect.entity.DocumentMetadata;
import com.jangveer.docintellect.entity.DocumentStatus;
import com.jangveer.docintellect.exception.DocumentNotFoundException;
import com.jangveer.docintellect.exception.DocumentDeletionException;
import com.jangveer.docintellect.exception.InvalidDocumentBatchException;
import com.jangveer.docintellect.exception.DocumentStorageException;
import com.jangveer.docintellect.exception.InvalidPdfException;
import com.jangveer.docintellect.exception.DocumentIndexingException;
import com.jangveer.docintellect.repository.DocumentMetadataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentService.class);
    private static final byte[] PDF_SIGNATURE = {'%', 'P', 'D', 'F', '-'};
    private static final int MAX_BATCH_DELETE_IDS = 100;

    private final DocumentMetadataRepository repository;
    private final LocalFileStorageService storageService;
    private final DocumentIngestionService ingestionService;
    private final DocumentProperties documentProperties;
    private final VectorStore vectorStore;
    private final DocumentOperationCoordinator operationCoordinator;

    public DocumentService(
            DocumentMetadataRepository repository,
            LocalFileStorageService storageService,
            DocumentIngestionService ingestionService,
            DocumentProperties documentProperties,
            VectorStore vectorStore,
            DocumentOperationCoordinator operationCoordinator
    ) {
        this.repository = repository;
        this.storageService = storageService;
        this.ingestionService = ingestionService;
        this.documentProperties = documentProperties;
        this.vectorStore = vectorStore;
        this.operationCoordinator = operationCoordinator;
    }

    public DocumentResponse upload(MultipartFile file, UUID userId) {
        validate(file);
        String storagePath;
        try {
            storagePath = storageService.store(file);
        } catch (IOException exception) {
            throw new DocumentStorageException("Unable to store the uploaded PDF.", exception);
        }

        DocumentMetadata metadata = new DocumentMetadata(
                file.getOriginalFilename(),
                "application/pdf",
                file.getSize(),
                storagePath,
                userId
        );
        try {
            metadata = repository.save(metadata);
        } catch (RuntimeException exception) {
            try {
                storageService.delete(storagePath);
            } catch (IOException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }

        try {
            ingestionService.index(metadata);
        } catch (TaskRejectedException exception) {
            metadata.setStatus(DocumentStatus.FAILED);
            repository.save(metadata);
            throw new DocumentIndexingException("The document was saved but the indexing queue is full.", exception);
        }
        return DocumentResponse.from(metadata);
    }

    public BatchUploadResponse uploadBatch(List<MultipartFile> files, UUID userId) {
        if (files == null || files.isEmpty() || files.size() > documentProperties.maxBatchFiles()) {
            throw new InvalidDocumentBatchException(
                    "Upload between 1 and " + documentProperties.maxBatchFiles() + " PDF files per batch."
            );
        }

        List<BatchUploadItemResult> results = files.stream()
                .map(file -> uploadBatchItem(file, userId))
                .toList();
        return new BatchUploadResponse(results);
    }

    private BatchUploadItemResult uploadBatchItem(MultipartFile file, UUID userId) {
        String fileName = file == null ? null : file.getOriginalFilename();
        try {
            return BatchUploadItemResult.success(upload(file, userId));
        } catch (InvalidPdfException | DocumentStorageException | DocumentIndexingException exception) {
            return BatchUploadItemResult.failure(fileName, exception.getMessage());
        } catch (DataAccessException exception) {
            logger.error("Unable to persist metadata for uploaded file {}", fileName, exception);
            return BatchUploadItemResult.failure(fileName, "Unable to save document metadata.");
        }
    }

    public DocumentResponse get(UUID id, UUID userId) {
        return DocumentResponse.from(repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new DocumentNotFoundException(id)));
    }

    public List<DocumentResponse> list(UUID userId) {
        return repository.findAllByUserIdOrderByUploadedAtDesc(userId)
                .stream()
                .map(DocumentResponse::from)
                .toList();
    }

    public void delete(UUID id, UUID userId) {
        operationCoordinator.execute(id, () -> deleteDocument(id, userId));
    }

    public BatchDeleteResponse deleteBatch(List<UUID> ids, UUID userId) {
        if (ids == null || ids.isEmpty() || ids.size() > MAX_BATCH_DELETE_IDS
                || ids.stream().anyMatch(id -> id == null)) {
            throw new InvalidDocumentBatchException(
                    "Provide between 1 and " + MAX_BATCH_DELETE_IDS + " document IDs per batch."
            );
        }

        List<BatchDeleteItemResult> results = ids.stream()
                .map(id -> deleteBatchItem(id, userId))
                .toList();
        return new BatchDeleteResponse(results);
    }

    private BatchDeleteItemResult deleteBatchItem(UUID id, UUID userId) {
        String fileName = null;
        try {
            DocumentResponse document = get(id, userId);
            fileName = document.fileName();
            delete(id, userId);
            return BatchDeleteItemResult.success(id, fileName);
        } catch (DocumentNotFoundException exception) {
            return BatchDeleteItemResult.failure(id, fileName, exception.getMessage());
        } catch (DocumentDeletionException | DocumentStorageException exception) {
            return BatchDeleteItemResult.failure(id, fileName, exception.getMessage());
        } catch (DataAccessException exception) {
            logger.error("Unable to delete metadata for document {}", id, exception);
            return BatchDeleteItemResult.failure(id, fileName, "Unable to delete document metadata.");
        }
    }

    private void deleteDocument(UUID id, UUID userId) {
        DocumentMetadata metadata = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new DocumentNotFoundException(id));

        try {
            vectorStore.delete(new Filter.Expression(
                    Filter.ExpressionType.AND,
                    new Filter.Expression(
                            Filter.ExpressionType.EQ,
                            new Filter.Key("documentId"),
                            new Filter.Value(id.toString())
                    ),
                    new Filter.Expression(
                            Filter.ExpressionType.EQ,
                            new Filter.Key("userId"),
                            new Filter.Value(userId.toString())
                    )
            ));
        } catch (RuntimeException exception) {
            throw new DocumentDeletionException("Unable to delete the document's indexed content.", exception);
        }

        try {
            storageService.delete(metadata.getStoragePath());
        } catch (IOException exception) {
            throw new DocumentStorageException(
                    "Indexed content was removed, but the stored PDF could not be deleted. Retry the request.",
                    exception
            );
        }

        repository.delete(metadata);
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidPdfException("A non-empty PDF file is required.");
        }
        if (file.getSize() > documentProperties.maxFileSizeBytes()) {
            throw new InvalidPdfException("PDF uploads are limited to 10 MB.");
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")) {
            throw new InvalidPdfException("Only files with a .pdf extension are accepted.");
        }
        try (var input = file.getInputStream()) {
            byte[] signature = input.readNBytes(PDF_SIGNATURE.length);
            if (!java.util.Arrays.equals(signature, PDF_SIGNATURE)) {
                throw new InvalidPdfException("The uploaded file does not have a valid PDF signature.");
            }
        } catch (IOException exception) {
            throw new InvalidPdfException("Unable to read the uploaded PDF.");
        }
    }
}
