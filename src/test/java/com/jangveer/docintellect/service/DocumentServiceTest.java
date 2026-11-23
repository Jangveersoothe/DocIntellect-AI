package com.jangveer.docintellect.service;

import com.jangveer.docintellect.config.DocumentProperties;
import com.jangveer.docintellect.dto.BatchDeleteResponse;
import com.jangveer.docintellect.dto.BatchUploadResponse;
import com.jangveer.docintellect.dto.DocumentResponse;
import com.jangveer.docintellect.entity.DocumentMetadata;
import com.jangveer.docintellect.exception.InvalidDocumentBatchException;
import com.jangveer.docintellect.exception.DocumentNotFoundException;
import com.jangveer.docintellect.exception.InvalidPdfException;
import com.jangveer.docintellect.repository.DocumentMetadataRepository;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DocumentServiceTest {

    @Test
    void storesValidPdfAndStartsIngestion() throws IOException {
        DocumentMetadataRepository repository = mock(DocumentMetadataRepository.class);
        LocalFileStorageService storage = mock(LocalFileStorageService.class);
        DocumentIngestionService ingestion = mock(DocumentIngestionService.class);
        when(storage.store(any())).thenReturn("document.pdf");
        when(repository.save(any(DocumentMetadata.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DocumentService service = new DocumentService(
                repository,
                storage,
                ingestion,
                new DocumentProperties(10 * 1024 * 1024, 10),
                mock(VectorStore.class),
                new DocumentOperationCoordinator()
        );
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "policy.pdf",
                "application/pdf",
                "%PDF-1.7\ntest".getBytes()
        );

        DocumentResponse response = service.upload(file);

        assertEquals("policy.pdf", response.fileName());
        verify(storage).store(file);
        verify(repository).save(any(DocumentMetadata.class));
        verify(ingestion).index(any(DocumentMetadata.class));
    }

    @Test
    void rejectsFilenameWithoutPdfExtension() {
        DocumentMetadataRepository repository = mock(DocumentMetadataRepository.class);
        DocumentService service = new DocumentService(
                repository,
                mock(LocalFileStorageService.class),
                mock(DocumentIngestionService.class),
                new DocumentProperties(10 * 1024 * 1024, 10),
                mock(VectorStore.class),
                new DocumentOperationCoordinator()
        );
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "policy.txt",
                "text/plain",
                "%PDF-1.7\ntest".getBytes()
        );

        assertThrows(InvalidPdfException.class, () -> service.upload(file));
    }

    @Test
    void batchUploadReportsEachFileOutcome() throws IOException {
        DocumentMetadataRepository repository = mock(DocumentMetadataRepository.class);
        LocalFileStorageService storage = mock(LocalFileStorageService.class);
        DocumentIngestionService ingestion = mock(DocumentIngestionService.class);
        when(storage.store(any())).thenReturn("valid-policy.pdf");
        when(repository.save(any(DocumentMetadata.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        DocumentService service = new DocumentService(
                repository,
                storage,
                ingestion,
                new DocumentProperties(10 * 1024 * 1024, 10),
                mock(VectorStore.class),
                new DocumentOperationCoordinator()
        );
        MockMultipartFile validFile = new MockMultipartFile(
                "files",
                "policy.pdf",
                "application/pdf",
                "%PDF-1.7\ntest".getBytes()
        );
        MockMultipartFile invalidFile = new MockMultipartFile(
                "files",
                "notes.txt",
                "text/plain",
                "not a pdf".getBytes()
        );

        BatchUploadResponse response = service.uploadBatch(List.of(validFile, invalidFile));

        assertEquals(2, response.results().size());
        assertTrue(response.results().get(0).successful());
        assertEquals("policy.pdf", response.results().get(0).document().fileName());
        assertFalse(response.results().get(1).successful());
        assertEquals("Only files with a .pdf extension are accepted.", response.results().get(1).error());
        verify(ingestion).index(any(DocumentMetadata.class));
    }

    @Test
    void rejectsBatchLargerThanConfiguredLimit() {
        DocumentService service = new DocumentService(
                mock(DocumentMetadataRepository.class),
                mock(LocalFileStorageService.class),
                mock(DocumentIngestionService.class),
                new DocumentProperties(10 * 1024 * 1024, 1),
                mock(VectorStore.class),
                new DocumentOperationCoordinator()
        );

        assertThrows(
                InvalidDocumentBatchException.class,
                () -> service.uploadBatch(List.of(
                        new MockMultipartFile("files", "one.pdf", "application/pdf", new byte[]{1}),
                        new MockMultipartFile("files", "two.pdf", "application/pdf", new byte[]{1})
                ))
        );
    }

    @Test
    void deletesVectorsFileAndMetadata() throws IOException {
        DocumentMetadataRepository repository = mock(DocumentMetadataRepository.class);
        LocalFileStorageService storage = mock(LocalFileStorageService.class);
        VectorStore vectorStore = mock(VectorStore.class);
        DocumentMetadata metadata = new DocumentMetadata(
                "policy.pdf",
                "application/pdf",
                100,
                "stored-policy.pdf"
        );
        when(repository.findById(metadata.getId())).thenReturn(Optional.of(metadata));

        DocumentService service = new DocumentService(
                repository,
                storage,
                mock(DocumentIngestionService.class),
                new DocumentProperties(10 * 1024 * 1024, 10),
                vectorStore,
                new DocumentOperationCoordinator()
        );

        service.delete(metadata.getId());

        org.mockito.ArgumentCaptor<Filter.Expression> filterCaptor =
                org.mockito.ArgumentCaptor.forClass(Filter.Expression.class);
        verify(vectorStore).delete(filterCaptor.capture());
        assertEquals(Filter.ExpressionType.EQ, filterCaptor.getValue().type());
        assertEquals(new Filter.Key("documentId"), filterCaptor.getValue().left());
        assertEquals(new Filter.Value(metadata.getId().toString()), filterCaptor.getValue().right());
        verify(storage).delete("stored-policy.pdf");
        verify(repository).delete(metadata);
    }

    @Test
    void deletingUnknownDocumentDoesNotChangeStoredData() {
        DocumentMetadataRepository repository = mock(DocumentMetadataRepository.class);
        LocalFileStorageService storage = mock(LocalFileStorageService.class);
        VectorStore vectorStore = mock(VectorStore.class);
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        DocumentService service = new DocumentService(
                repository,
                storage,
                mock(DocumentIngestionService.class),
                new DocumentProperties(10 * 1024 * 1024, 10),
                vectorStore,
                new DocumentOperationCoordinator()
        );

        assertThrows(DocumentNotFoundException.class, () -> service.delete(id));

        verifyNoInteractions(storage, vectorStore);
        verify(repository, never()).delete(any(DocumentMetadata.class));
    }

    @Test
    void batchDeleteContinuesAfterAnUnknownId() throws IOException {
        DocumentMetadataRepository repository = mock(DocumentMetadataRepository.class);
        LocalFileStorageService storage = mock(LocalFileStorageService.class);
        VectorStore vectorStore = mock(VectorStore.class);
        DocumentMetadata metadata = new DocumentMetadata(
                "policy.pdf",
                "application/pdf",
                100,
                "stored-policy.pdf"
        );
        UUID missingId = UUID.randomUUID();
        when(repository.findById(metadata.getId())).thenReturn(Optional.of(metadata));
        when(repository.findById(missingId)).thenReturn(Optional.empty());
        DocumentService service = new DocumentService(
                repository,
                storage,
                mock(DocumentIngestionService.class),
                new DocumentProperties(10 * 1024 * 1024, 10),
                vectorStore,
                new DocumentOperationCoordinator()
        );

        BatchDeleteResponse response = service.deleteBatch(List.of(metadata.getId(), missingId));

        assertEquals(2, response.results().size());
        assertTrue(response.results().get(0).successful());
        assertEquals("policy.pdf", response.results().get(0).fileName());
        assertFalse(response.results().get(1).successful());
        assertEquals(missingId, response.results().get(1).documentId());
        verify(repository).delete(metadata);
        verify(storage).delete("stored-policy.pdf");
    }
}
