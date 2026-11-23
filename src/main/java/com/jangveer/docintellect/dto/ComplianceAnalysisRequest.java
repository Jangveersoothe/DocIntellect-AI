package com.jangveer.docintellect.dto;

import jakarta.validation.constraints.NotBlank;

public record ComplianceAnalysisRequest(@NotBlank String query) {
}
