package com.careerpilot.stream;

import com.careerpilot.TestConversations;
import com.careerpilot.TestSecurity;
import com.careerpilot.chat.*;
import com.careerpilot.config.RagConfig;
import com.careerpilot.config.StreamingContextConfig;
import com.careerpilot.localtest.*;
import com.careerpilot.observability.*;
import com.careerpilot.service.*;
import com.careerpilot.web.ApiExceptionHandler;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class StreamingChatTest {
    TestConversations db;
    StreamingChatService service;
    RunRepository runs;
    ObservabilityService observations;
    UserProfileService profiles;
    MockMvc http;
    LocalTestChatModel model;

    @BeforeEach void setup() {
        TestSecurity.as(1);
        new StreamingContextConfig();
        db = TestConversations.create();
        runs = new RunRepository(db.jdbc(), db.repository());
        observations = mock(ObservabilityService.class);
        profiles = mock(UserProfileService.class);
        when(profiles.getResume()).thenAnswer(invocation -> {
            assertThat(com.careerpilot.security.CurrentUser.id()).isEqualTo(1);
            return new ResumeProfile(1L, "Fixture", "Software", "Java", List.of("Java"), "");
        });
        model = new LocalTestChatModel(new LocalTestEmbeddingModel(true, 1536));
        ReflectionTestUtils.setField(model, "streamDelayMs", 1L);
        useModel(model);
    }
    void useModel(org.springframework.ai.chat.model.ChatModel transport) {
        VectorStore vectors = mock(VectorStore.class);
        when(vectors.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        ChatService chat = new ChatService(ChatClient.builder(transport), new ResumeTools(profiles),
                new JobAnalysisTools(mock(JobService.class)), new GreetingTools(mock(JobService.class)),
                new JobPersistenceTools(mock(JobPersistenceService.class)), new ApplicationTools(mock(ApplicationService.class)),
                db.memory(), new RagConfig().knowledgeAdvisor(new ObservedVectorStore(vectors)), observations, db.service());
        service = new StreamingChatService(chat, db.service(), runs, observations);
        http = MockMvcBuilders.standaloneSetup(new StreamingChatController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }
    @AfterEach void cleanup() { service.shutdown(); TestConversations.closeCreated(); }

    MvcResult open(String conversation, String text) throws Exception {
        return http.perform(post("/api/chat/stream").contentType(MediaType.APPLICATION_JSON)
                .content("{\"conversationId\":\"" + conversation + "\",\"message\":\"" + text + "\"}")).andReturn();
    }
    String finish(MvcResult request) throws Exception {
        request.getAsyncResult(10_000);
        var done = http.perform(asyncDispatch(request)).andReturn();
        assertThat(done.getResponse().getStatus()).isEqualTo(200);
        return done.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }
    String runId() { return db.jdbc().queryForObject("SELECT run_id FROM ai_run", String.class); }

    @Test void sseOrderFinalizationAndPersistentMemory() throws Exception {
        String output = finish(open("test-001", "My streaming memory value is STREAM-A-12345"));
        assertThat(output).contains("event:run.started", "event:memory.loaded", "event:rag.started", "event:rag.completed",
                "event:llm.started", "event:token", "event:message.completed", "event:run.completed");
        assertThat(output.indexOf("event:run.started")).isLessThan(output.indexOf("event:memory.loaded"));
        assertThat(output.indexOf("event:memory.loaded")).isLessThan(output.indexOf("event:rag.started"));
        assertThat(output.indexOf("event:rag.completed")).isLessThan(output.indexOf("event:llm.started"));
        assertThat(output.indexOf("event:token")).isLessThan(output.indexOf("event:message.completed"));
        var row = runs.owned(1, runId());
        assertThat(row.status()).isEqualTo("COMPLETED");
        assertThat(row.timeToFirstTokenMs()).isGreaterThanOrEqualTo(0);
        StringBuilder tokens = new StringBuilder();
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        for (String block : output.split("\n\n")) if (block.startsWith("event:token"))
            tokens.append(mapper.readTree(block.substring(block.indexOf("data:") + 5)).get("delta").asText());
        assertThat(db.repository().history(1, "test-001", 0, 100).items().getLast().content()).isEqualTo(tokens.toString());
        assertThat(db.repository().history(1, "test-001", 0, 100).items()).hasSize(2);
        finish(open("test-001", "What is my streaming memory value?"));
        assertThat(db.repository().history(1, "test-001", 0, 100).items().getLast().content()).contains("STREAM-A-12345");
        var captured = org.mockito.ArgumentCaptor.forClass(ObservationScope.Trace.class);
        verify(observations, times(2)).recordSafely(captured.capture(), any(), nullable(String.class), isNull());
        assertThat(captured.getValue().events()).anyMatch(event -> "MEMORY".equals(event.getEventType()) && event.getMemoryMessageCount() == 2);
        finish(open("test-001", "My final streaming memory value is FINAL-STREAM-12345"));
        finish(open("test-001", "What is my final streaming memory value?"));
        assertThat(db.repository().history(1, "test-001", 0, 100).items().getLast().content()).contains("FINAL-STREAM-12345");
    }

    @Test void failureKeepsOnlyUserAndNoCompletedAssistant() throws Exception {
        String output = finish(open("test-001", "LOCAL_TEST_FAIL_STREAM"));
        assertThat(output).contains("event:token", "event:run.failed").doesNotContain("event:message.completed");
        assertThat(runs.owned(1, runId()).status()).isEqualTo("FAILED");
        assertThat(db.repository().history(1, "test-001", 0, 100).items()).hasSize(1);
    }

    @Test void toolRunsWithAuthenticatedOwnerAcrossReactorSchedulers() throws Exception {
        String output = finish(open("test-001", "LOCAL_TEST_TOOL getResume"));
        assertThat(output).contains("event:tool.started", "event:tool.completed", "event:message.completed");
        verify(profiles).getResume();
        assertThat(output.indexOf("event:tool.completed")).isLessThan(output.indexOf("event:token"));
        assertThat(db.repository().history(1, "test-001", 0, 100).items()).hasSize(2);
    }

    @Test void cancelIdorAndConversationExclusivity() throws Exception {
        ReflectionTestUtils.setField(model, "streamDelayMs", 200L);
        MvcResult pending = open("test-001", "Slow cancellation fixture");
        String id = runId();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!pending.getResponse().getContentAsString().contains("event:token") && System.nanoTime() < deadline) Thread.sleep(10);
        assertThat(pending.getResponse().getContentAsString()).contains("event:token");
        assertThat(open("test-001", "Duplicate").getResponse().getStatus()).isEqualTo(409);
        TestSecurity.as(2);
        assertThat(http.perform(post("/api/chat/runs/" + id + "/cancel")).andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(http.perform(get("/api/chat/runs/" + id)).andReturn().getResponse().getStatus()).isEqualTo(404);
        TestSecurity.as(1);
        assertThat(service.cancel(id).status()).isEqualTo("CANCELLED");
        String output = finish(pending);
        assertThat(output).contains("CANCELLED").doesNotContain("event:message.completed");
        assertThat(db.repository().history(1, "test-001", 0, 100).items()).hasSize(1);
        assertThat(runs.owned(1, id).outputChunkCount()).isLessThan(5);
    }

    @Test void streamsNeverLoadAnotherOwnersConversation() throws Exception {
        String own = db.repository().create(1, java.util.UUID.randomUUID().toString()).conversationId();
        TestSecurity.as(2);
        assertThat(open(own, "Attack").getResponse().getStatus()).isEqualTo(404);
        finish(open("test-002", "My streaming memory value is STREAM-B-98765"));
        TestSecurity.as(1);
        finish(open("test-002", "What is my streaming memory value?"));
        assertThat(db.repository().history(1, "test-002", 0, 100).items().getLast().content()).doesNotContain("STREAM-B-98765");
    }

    @Test void observationFailureDoesNotFailCompletedStream() throws Exception {
        doThrow(new IllegalStateException("fixture database error")).when(observations).recordSafely(any(), any(), nullable(String.class), any());
        String output = finish(open("test-001", "Hello"));
        assertThat(output).contains("event:message.completed", "event:run.completed").doesNotContain("event:run.failed");
        assertThat(runs.owned(1, runId()).status()).isEqualTo("COMPLETED");
    }

    @Test void immediateCancellationStillPreservesCommittedUser() throws Exception {
        ReflectionTestUtils.setField(model, "streamDelayMs", 1000L);
        MvcResult pending = open("test-001", "Cancel before first text");
        assertThat(service.cancel(runId()).status()).isEqualTo("CANCELLED");
        finish(pending);
        assertThat(db.repository().history(1, "test-001", 0, 100).items()).hasSize(1);
        assertThat(db.repository().history(1, "test-001", 0, 100).items().getFirst().role()).isEqualTo("USER");
    }

    @Test void officialToolAdvisorAccumulatesTwoModelRoundsWithoutDuplicatingText() throws Exception {
        var fake = mock(org.springframework.ai.chat.model.ChatModel.class);
        when(fake.getOptions()).thenReturn(org.springframework.ai.model.tool.ToolCallingChatOptions.builder().build());
        when(fake.stream(any(org.springframework.ai.chat.prompt.Prompt.class))).thenAnswer(invocation -> {
            org.springframework.ai.chat.prompt.Prompt prompt = invocation.getArgument(0);
            boolean toolDone = prompt.getInstructions().stream().anyMatch(org.springframework.ai.chat.messages.ToolResponseMessage.class::isInstance);
            var metadata = org.springframework.ai.chat.metadata.ChatResponseMetadata.builder().model("mock-provider")
                    .usage(new org.springframework.ai.chat.metadata.DefaultUsage(toolDone ? 200 : 100, toolDone ? 20 : 50, toolDone ? 220 : 150)).build();
            if (toolDone) return reactor.core.publisher.Flux.just(new org.springframework.ai.chat.model.ChatResponse(
                    List.of(new org.springframework.ai.chat.model.Generation(new org.springframework.ai.chat.messages.AssistantMessage("Safe answer"))), metadata));
            var preface = new org.springframework.ai.chat.model.ChatResponse(List.of(new org.springframework.ai.chat.model.Generation(
                    new org.springframework.ai.chat.messages.AssistantMessage("Checking resume. "))),
                    org.springframework.ai.chat.metadata.ChatResponseMetadata.builder().model("mock-provider").usage(null).build());
            var tool = org.springframework.ai.chat.messages.AssistantMessage.builder().toolCalls(List.of(
                    new org.springframework.ai.chat.messages.AssistantMessage.ToolCall("mock-resume", "function", "getResume", "{}"))).build();
            return reactor.core.publisher.Flux.just(preface, new org.springframework.ai.chat.model.ChatResponse(
                    List.of(new org.springframework.ai.chat.model.Generation(tool)), metadata));
        });
        useModel(fake);
        String output = finish(open("test-001", "Mock tool loop usage"));
        assertThat(output).contains("event:message.completed").doesNotContain("event:run.failed");
        assertThat(db.repository().history(1, "test-001", 0, 100).items().getLast().content()).isEqualTo("Checking resume. Safe answer");
        var response = org.mockito.ArgumentCaptor.forClass(org.springframework.ai.chat.model.ChatResponse.class);
        verify(observations).recordSafely(any(), response.capture(), nullable(String.class), isNull());
        assertThat(response.getValue().getMetadata().getUsage().getPromptTokens()).isEqualTo(300);
        assertThat(response.getValue().getMetadata().getUsage().getCompletionTokens()).isEqualTo(70);
        assertThat(response.getValue().getMetadata().getUsage().getTotalTokens()).isEqualTo(370);
    }
}
