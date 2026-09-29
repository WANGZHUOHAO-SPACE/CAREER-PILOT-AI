package com.careerpilot.rag;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import com.careerpilot.TestSecurity;
import org.junit.jupiter.api.BeforeEach;

import com.careerpilot.chat.ApplicationTools;
import com.careerpilot.chat.ChatService;
import com.careerpilot.chat.GreetingTools;
import com.careerpilot.chat.JobAnalysis;
import com.careerpilot.chat.JobAnalysisTools;
import com.careerpilot.chat.JobPersistenceTools;
import com.careerpilot.chat.JobService;
import com.careerpilot.chat.ResumeProfile;
import com.careerpilot.chat.ResumeTools;
import com.careerpilot.TestConversations;
import com.careerpilot.config.RagConfig;
import com.careerpilot.service.ApplicationService;
import com.careerpilot.service.JobPersistenceService;
import com.careerpilot.service.UserProfileService;
import com.careerpilot.observability.ObservationScope;
import com.careerpilot.observability.ObservabilityService;
import com.careerpilot.observability.ObservedVectorStore;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagChatIntegrationTest {
    @BeforeEach void authenticate() { TestSecurity.as(1); }
    @org.junit.jupiter.api.AfterEach void closePools() { TestConversations.closeCreated(); }

    @Test
    void uploadedMarkdownAnswersDatabaseQuestion() {
        Fixture fixture = fixture();
        fixture.uploadProject();

        assertEquals("MySQL", fixture.chat.reply("rag-test-001", "CareerPilot AI用了什么数据库？"));
        assertTrue(fixture.prompts.getLast().getUserMessage().getText().contains("MySQL"));
    }

    @Test
    void absentKubernetesExperienceIsNotInvented() {
        Fixture fixture = fixture();
        fixture.uploadProject();

        assertEquals("当前知识库没有找到相关资料。",
                fixture.chat.reply("rag-test-001", "我有哪些Kubernetes项目经历？"));
        assertFalse(fixture.prompts.getLast().getUserMessage().getText().contains("MySQL开发"));
    }

    @Test
    void memoryRagAndBothReadOnlyToolsWorkInOneConversation() {
        Fixture fixture = fixture();
        fixture.uploadProject();

        assertEquals("MySQL", fixture.chat.reply("rag-test-001", "CareerPilot AI用了什么数据库？"));
        assertEquals("Spring AI", fixture.chat.reply("rag-test-001", "那这个项目还用了什么AI框架？"));
        Prompt followUpPrompt = fixture.prompts.getLast();
        assertTrue(followUpPrompt.getUserMessages().stream()
                .anyMatch(message -> message.getText().contains("什么数据库")));
        assertTrue(followUpPrompt.getUserMessage().getText().contains("Spring AI"));
        assertEquals(4, fixture.memory.get("u:1:c:rag-test-001").size());
        assertEquals("CareerPilot AI用了什么数据库？", fixture.memory.get("u:1:c:rag-test-001").getFirst().getText());

        String answer = fixture.chat.reply("rag-test-001",
                "结合我的简历和项目知识库，分析这个Java后端岗位：JD要求Spring Boot和MySQL。");
        assertEquals("简历、项目与JD已结合分析", answer);
        verify(fixture.profileService, times(1)).getResume();
        verify(fixture.jobService, times(1)).analyzeJob(any(String.class));
        assertTrue(fixture.prompts.getLast().getUserMessage().getText().contains("CareerPilot AI"));
        assertEquals(6, fixture.memory.get("u:1:c:rag-test-001").size());
        assertFalse(fixture.memory.get("u:1:c:rag-test-001").stream()
                .anyMatch(message -> message instanceof ToolResponseMessage));
        var observed = org.mockito.ArgumentCaptor.forClass(ObservationScope.Trace.class);
        verify(fixture.observability, times(3)).recordSafely(observed.capture(), any(),
                org.mockito.ArgumentMatchers.nullable(String.class),
                org.mockito.ArgumentMatchers.isNull());
        ObservationScope.Trace lastTrace = observed.getValue();
        assertTrue(lastTrace.events().stream().anyMatch(event -> "RAG_SEARCH".equals(event.getEventType())));
        assertEquals(2, lastTrace.events().stream()
                .filter(event -> "TOOL_CALL".equals(event.getEventType())).count());
    }

    @Test
    void retrievalFailureReturnsSafeServiceError() {
        SimpleVectorStore store = mock(SimpleVectorStore.class);
        when(store.similaritySearch(any(SearchRequest.class)))
                .thenThrow(new IllegalStateException("internal vector store detail"));
        ChatModel model = mock(ChatModel.class);
        ObservabilityService observability = mock(ObservabilityService.class);
        when(model.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        var database = TestConversations.create();
        RagConfig ragConfig = new RagConfig();
        ChatService chat = new ChatService(ChatClient.builder(model),
                new ResumeTools(mock(UserProfileService.class)),
                new JobAnalysisTools(mock(JobService.class)),
                new GreetingTools(mock(JobService.class)),
                new JobPersistenceTools(mock(JobPersistenceService.class)),
                new ApplicationTools(mock(ApplicationService.class)),
                database.memory(),
                ragConfig.knowledgeAdvisor(new ObservedVectorStore(store)), observability, database.service());

        KnowledgeException exception = assertThrows(KnowledgeException.class,
                () -> chat.reply("rag-test-001", "我的项目用了什么数据库？"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.status());
        assertFalse(exception.getMessage().contains("internal vector store detail"));
        var trace = org.mockito.ArgumentCaptor.forClass(ObservationScope.Trace.class);
        verify(observability).recordSafely(trace.capture(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.nullable(String.class), any(Throwable.class));
        assertTrue(trace.getValue().ragFailed());
    }

    @Test
    void advisorDoesNotSendAnotherUsersDocumentsToModel() {
        Fixture fixture = fixture();
        fixture.uploadProject();
        fixture.chat.reply("same-test", "CareerPilot AI用了什么数据库？");
        TestSecurity.as(2);
        fixture.knowledge.upload(new MockMultipartFile("file", "user-b.md", "text/markdown",
                "User B 的独立项目使用 PostgreSQL。".getBytes(StandardCharsets.UTF_8)));
        fixture.chat.reply("same-test", "我的项目数据库是什么？");
        Prompt prompt = fixture.prompts.getLast();
        assertFalse(prompt.getUserMessage().getText().contains("CareerPilot AI项目使用"));
        assertTrue(prompt.getUserMessage().getText().contains("PostgreSQL"));
        assertFalse(prompt.getUserMessages().stream()
                .anyMatch(message -> message.getText().contains("CareerPilot AI用了什么数据库")));
    }

    @Test
    void observationFailureCannotReplaceAiAnswer() {
        Fixture fixture = fixture();
        org.mockito.Mockito.doThrow(new IllegalStateException("observation database unavailable"))
                .when(fixture.observability).recordSafely(any(), any(),
                        org.mockito.ArgumentMatchers.nullable(String.class),
                        org.mockito.ArgumentMatchers.isNull());
        fixture.uploadProject();
        assertEquals("MySQL", fixture.chat.reply("observation-failure", "CareerPilot AI用了什么数据库？"));
    }

    private static Fixture fixture() {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.dimensions()).thenReturn(2);
        when(embeddingModel.embed(any(Document.class))).thenAnswer(invocation -> vector(
                invocation.<Document>getArgument(0).getText()));
        when(embeddingModel.embed(any(String.class))).thenAnswer(invocation -> vector(invocation.getArgument(0)));

        RagConfig ragConfig = new RagConfig();
        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();
        KnowledgeService knowledge = new KnowledgeService(new KnowledgeDocumentLoader(),
                ragConfig.knowledgeTextSplitter(), store, mock(VectorDocumentRepository.class));
        var database = TestConversations.create();
        ChatMemory memory = database.memory();

        UserProfileService profileService = mock(UserProfileService.class);
        when(profileService.getResume()).thenReturn(new ResumeProfile(1L, "林同学", "软件工程",
                "Java后端开发实习", List.of("Java", "Spring Boot", "MySQL"), "求职简介"));
        JobService jobService = mock(JobService.class);
        when(jobService.analyzeJob(any(String.class))).thenReturn(new JobAnalysis("Java后端开发实习",
                List.of("Spring Boot", "MySQL"), List.of("Spring Boot", "MySQL"), List.of(), "匹配"));

        List<Prompt> prompts = new ArrayList<>();
        ChatModel model = mock(ChatModel.class);
        ObservabilityService observability = mock(ObservabilityService.class);
        when(model.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            prompts.add(prompt);
            return answer(prompt);
        });
        ChatService chat = new ChatService(ChatClient.builder(model), new ResumeTools(profileService),
                new JobAnalysisTools(jobService), new GreetingTools(jobService),
                new JobPersistenceTools(mock(JobPersistenceService.class)),
                new ApplicationTools(mock(ApplicationService.class)), memory,
                ragConfig.knowledgeAdvisor(new ObservedVectorStore(store)), observability, database.service());
        return new Fixture(chat, knowledge, memory, prompts, profileService, jobService, observability);
    }

    private static float[] vector(String text) {
        return text.contains("Kubernetes") ? new float[] {0, 1} : new float[] {1, 0};
    }

    private static ChatResponse answer(Prompt prompt) {
        String question = prompt.getUserMessage().getText();
        if (question.contains("结合我的简历")) {
            long toolResponses = prompt.getInstructions().stream()
                    .filter(message -> message instanceof ToolResponseMessage).count();
            if (toolResponses == 0) {
                return toolCall("getResume", "{}");
            }
            if (toolResponses == 1) {
                return toolCall("analyzeJob", "{\"jd\":\"Java后端岗位，要求Spring Boot和MySQL\"}");
            }
            return response("简历、项目与JD已结合分析");
        }
        if (question.contains("Kubernetes")) {
            return response("当前知识库没有找到相关资料。");
        }
        if (question.contains("AI框架")) {
            return response(question.contains("Spring AI") ? "Spring AI" : "当前知识库没有找到相关资料。");
        }
        return response(question.contains("MySQL") ? "MySQL" : "当前知识库没有找到相关资料。");
    }

    private static ChatResponse toolCall(String name, String arguments) {
        return new ChatResponse(List.of(new Generation(AssistantMessage.builder()
                .toolCalls(List.of(new AssistantMessage.ToolCall(name + "-call", "function", name, arguments)))
                .build())));
    }

    private static ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private record Fixture(ChatService chat, KnowledgeService knowledge, ChatMemory memory,
            List<Prompt> prompts, UserProfileService profileService, JobService jobService,
            ObservabilityService observability) {
        private void uploadProject() {
            knowledge.upload(new MockMultipartFile("file", "projects.md", "text/markdown",
                    "CareerPilot AI项目使用Spring Boot、Spring AI、MySQL开发。".getBytes(StandardCharsets.UTF_8)));
        }
    }
}
