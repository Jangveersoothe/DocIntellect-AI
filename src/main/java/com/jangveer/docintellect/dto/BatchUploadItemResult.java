package com.jangveer.docintellect.dto;

public record BatchUploadItemResult(
        String fileName,
        boolean successful,
        DocumentResponse document,
        String error
) {

    public static BatchUploadItemResult success(DocumentResponse document) {
        return new BatchUploadItemResult(document.fileName(), true, document, null);
    }

    public static BatchUploadItemResult failure(String fileName, String error) {
        return new BatchUploadItemResult(fileName, false, null, error);
    }
}
