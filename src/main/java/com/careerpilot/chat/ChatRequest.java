package com.careerpilot.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(@NotBlank @Size(max = 128) String conversationId,
        @NotBlank @Size(max = 8000) String message) {
}
