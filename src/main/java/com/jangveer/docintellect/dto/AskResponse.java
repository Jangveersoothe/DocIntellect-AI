package com.jangveer.docintellect.dto;

import java.util.List;
import java.util.UUID;

public record AskResponse(String answer, List<UUID> sourceDocumentIds, List<SourceCitation> citations) {
}
