package com.jangveer.docintellect.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.bootstrap-owner")
public record BootstrapOwnerProperties(String email, String password) {
}
