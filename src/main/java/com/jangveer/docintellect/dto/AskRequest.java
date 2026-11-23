package com.jangveer.docintellect.dto;

import jakarta.validation.constraints.NotBlank;

public record AskRequest(@NotBlank String query) {
}
