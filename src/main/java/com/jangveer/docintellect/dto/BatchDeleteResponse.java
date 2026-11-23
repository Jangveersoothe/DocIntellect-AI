package com.jangveer.docintellect.dto;

import java.util.List;

public record BatchDeleteResponse(List<BatchDeleteItemResult> results) {

    public BatchDeleteResponse {
        results = List.copyOf(results);
    }
}
