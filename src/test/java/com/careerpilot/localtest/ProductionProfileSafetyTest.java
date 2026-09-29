package com.careerpilot.localtest;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration;
import com.careerpilot.config.ProductionAiKeyGuard;
import static org.assertj.core.api.Assertions.assertThat;

class ProductionProfileSafetyTest {
    @Test
    void missingProductionApiKeyFailsAndDoesNotFallback() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(OpenAiEmbeddingAutoConfiguration.class))
                .withUserConfiguration(ProductionAiKeyGuard.class, LocalTestEmbeddingModel.class, LocalTestChatModel.class)
                .withPropertyValues("spring.profiles.active=prod", "spring.ai.openai.api-key=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("OPENAI_API_KEY must be configured");
                });
    }
}
