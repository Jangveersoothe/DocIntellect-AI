package com.jangveer.docintellect.controller;

import com.jangveer.docintellect.dto.DocumentResponse;
import com.jangveer.docintellect.dto.BatchDeleteResponse;
import com.jangveer.docintellect.dto.BatchUploadResponse;
import com.jangveer.docintellect.dto.DeleteDocumentsRequest;
import com.jangveer.docintellect.security.CurrentUser;
import com.jangveer.docintellect.service.DocumentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentService documentService;
    private final CurrentUser currentUser;

    public DocumentController(DocumentService documentService, CurrentUser currentUser) {
        this.documentService = documentService;
        this.currentUser = currentUser;
    }

    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse upload(@RequestParam("file") MultipartFile file) {
        return documentService.upload(file, currentUser.id());
    }

    @PostMapping(path = "/upload/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BatchUploadResponse uploadBatch(@RequestParam("files") List<MultipartFile> files) {
        return documentService.uploadBatch(files, currentUser.id());
    }

    @GetMapping
    public List<DocumentResponse> list() {
        return documentService.list(currentUser.id());
    }

    @GetMapping("/{id}")
    public DocumentResponse get(@PathVariable UUID id) {
        return documentService.get(id, currentUser.id());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        documentService.delete(id, currentUser.id());
    }

    @DeleteMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public BatchDeleteResponse deleteBatch(@Valid @RequestBody DeleteDocumentsRequest request) {
        return documentService.deleteBatch(request.ids(), currentUser.id());
    }
}
