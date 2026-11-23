package com.jangveer.docintellect.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.net.URI;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidPdfException.class)
    ResponseEntity<ProblemDetail> handleInvalidPdf(InvalidPdfException exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid PDF", exception.getMessage(), request);
    }

    @ExceptionHandler(InvalidDocumentBatchException.class)
    ResponseEntity<ProblemDetail> handleInvalidDocumentBatch(
            InvalidDocumentBatchException exception,
            HttpServletRequest request
    ) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid document batch", exception.getMessage(), request);
    }

    @ExceptionHandler(DocumentNotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(DocumentNotFoundException exception, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Document not found", exception.getMessage(), request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ProblemDetail> handleUploadTooLarge(
            MaxUploadSizeExceededException exception,
            HttpServletRequest request
    ) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "File too large", "PDF uploads are limited to 10 MB.", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return problem(HttpStatus.BAD_REQUEST, "Validation failed", detail, request);
    }

    @ExceptionHandler(DocumentIndexingException.class)
    ResponseEntity<ProblemDetail> handleIndexing(
            DocumentIndexingException exception,
            HttpServletRequest request
    ) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Document indexing failed", exception.getMessage(), request);
    }

    @ExceptionHandler(DocumentStorageException.class)
    ResponseEntity<ProblemDetail> handleStorage(
            DocumentStorageException exception,
            HttpServletRequest request
    ) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Document storage failed", exception.getMessage(), request);
    }

    @ExceptionHandler(DocumentDeletionException.class)
    ResponseEntity<ProblemDetail> handleDeletion(
            DocumentDeletionException exception,
            HttpServletRequest request
    ) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Document deletion failed", exception.getMessage(), request);
    }

    private ResponseEntity<ProblemDetail> problem(
            HttpStatus status,
            String title,
            String detail,
            HttpServletRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(status).body(problem);
    }
}
