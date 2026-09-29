package com.careerpilot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Missing credentials fail configuration; there is no automatic local-model fallback. */
@Component
@Profile("!local-test-embedding")
public final class ProductionAiKeyGuard {
    public ProductionAiKeyGuard(@Value("${spring.ai.openai.api-key:}") String apiKey) {
        if (apiKey.isBlank()) throw new IllegalStateException("OPENAI_API_KEY must be configured outside the local-test-embedding profile");
    }
}
