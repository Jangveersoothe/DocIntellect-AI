package com.jangveer.docintellect.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rag")
public record RagProperties(int chunkSizeTokens, int chunkOverlapTokens, int topK) {
}
