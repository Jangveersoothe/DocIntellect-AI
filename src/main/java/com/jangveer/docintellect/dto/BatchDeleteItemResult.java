package com.jangveer.docintellect.dto;

import java.util.UUID;

public record BatchDeleteItemResult(
        UUID documentId,
        String fileName,
        boolean successful,
        String error
) {

    public static BatchDeleteItemResult success(UUID documentId, String fileName) {
        return new BatchDeleteItemResult(documentId, fileName, true, null);
    }

    public static BatchDeleteItemResult failure(UUID documentId, String fileName, String error) {
        return new BatchDeleteItemResult(documentId, fileName, false, error);
    }
}
