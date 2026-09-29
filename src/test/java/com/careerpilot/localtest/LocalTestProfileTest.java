package com.careerpilot.localtest;

import com.careerpilot.CareerPilotAiApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles({"prod", "local-test-embedding"})
@SpringBootTest(classes = CareerPilotAiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {"DB_PASSWORD=unused", "JWT_SECRET=local-profile-unit-test-secret-at-least-32-bytes",
                "app.pgvector.initialize-schema=false", "app.pgvector.validate-schema=false", "app.streaming.recover-on-startup=false",
                "LOCAL_TEST_EMBEDDING_ENABLED=true"})
class LocalTestProfileTest {
    @Autowired private ApplicationContext context;

    @Test
    void noApiKeyIsNeededAndNoOpenAiModelBeanExists() {
        assertThat(context.getBeansOfType(EmbeddingModel.class).values()).hasSize(1)
                .allMatch(model -> model instanceof LocalTestEmbeddingModel);
        assertThat(context.getBeansOfType(ChatModel.class).values()).hasSize(1)
                .allMatch(model -> model instanceof LocalTestChatModel);
        assertThat(context.getBeansOfType(OpenAiEmbeddingModel.class)).isEmpty();
        assertThat(context.getBeansOfType(OpenAiChatModel.class)).isEmpty();
        assertThat(context.getEnvironment().getProperty("spring.ai.openai.api-key")).isEmpty();
    }
}
