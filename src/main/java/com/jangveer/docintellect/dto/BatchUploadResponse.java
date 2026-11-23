package com.jangveer.docintellect.dto;

import java.util.List;

public record BatchUploadResponse(List<BatchUploadItemResult> results) {

    public BatchUploadResponse {
        results = List.copyOf(results);
    }
}
