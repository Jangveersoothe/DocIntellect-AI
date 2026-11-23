package com.jangveer.docintellect.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.documents")
public record DocumentProperties(long maxFileSizeBytes, int maxBatchFiles) {
}
