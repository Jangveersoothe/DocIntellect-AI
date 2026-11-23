package com.jangveer.docintellect.dto;

import java.util.List;

public record ComplianceAnalysisResult(
        boolean isCompliant,
        double riskScore,
        List<String> violations
) {
}
