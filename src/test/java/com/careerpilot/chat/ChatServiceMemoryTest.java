package com.careerpilot.chat;

import java.util.List;
import com.careerpilot.TestSecurity;
import org.junit.jupiter.api.BeforeEach;

import com.careerpilot.config.MemoryConfig;
import com.careerpilot.service.ApplicationService;
import com.careerpilot.service.JobPersistenceService;
import com.careerpilot.service.UserProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import com.careerpilot.TestConversations;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatServiceMemoryTest {
    @BeforeEach void authenticate() { TestSecurity.as(1); }
    @org.junit.jupiter.api.AfterEach void closePools() { TestConversations.closeCreated(); }

    @Test
    void remembersCityWithinConversationAndKeepsOtherConversationIsolated() {
        Fixture fixture = fixture();

        fixture.service.reply("test-001", "我想找杭州的Java后端实习。");
        assertEquals("杭州", fixture.service.reply("test-001", "我刚才想找什么城市的岗位？"));
        assertEquals("没有提到城市", fixture.service.reply("test-002", "我刚才想找什么城市的岗位？"));

        assertEquals(4, fixture.memory.get("u:1:c:test-001").size());
        assertEquals(2, fixture.memory.get("u:1:c:test-002").size());
    }

    @Test
    void toolCallingStillWorksAndStoresOnlyFinalTurnMessages() {
        Fixture fixture = fixture();

        fixture.service.reply("test-001", "我想找杭州的Java后端实习。");
        assertEquals("已读取简历", fixture.service.reply("test-001", "根据我的技术栈分析一下我适合什么岗位。"));

        verify(fixture.profileService, times(1)).getResume();
        assertEquals(4, fixture.memory.get("u:1:c:test-001").size());
        assertFalse(fixture.memory.get("u:1:c:test-001").stream().anyMatch(message -> message instanceof ToolResponseMessage));
    }

    @Test
    void limitsContextToTwentyMessages() {
        Fixture fixture = fixture();

        for (int turn = 0; turn < 12; turn++) {
            fixture.service.reply("test-001", "普通问题" + turn);
        }

        assertTrue(fixture.memory.get("u:1:c:test-001").size() <= MemoryConfig.MAX_MESSAGES);
        assertFalse(fixture.memory.get("u:1:c:test-001").stream().anyMatch(message -> "普通问题0".equals(message.getText())));
    }

    @Test
    void sameConversationIdDoesNotCrossUsers() {
        Fixture fixture = fixture();
        fixture.service.reply("same-test", "我想找杭州的Java后端实习。");
        TestSecurity.as(2);
        assertEquals("没有提到城市", fixture.service.reply("same-test", "我刚才想找什么城市的岗位？"));
        assertEquals(2, fixture.memory.get("u:2:c:same-test").size());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> fixture.memory.get("u:1:c:same-test"));
        TestSecurity.as(1);
        assertEquals(2, fixture.memory.get("u:1:c:same-test").size());
    }

    @Test
    void toolExecutionRetainsAuthenticatedUserContext() {
        Fixture fixture = fixture();
        TestSecurity.as(2);
        when(fixture.profileService.getResume()).thenAnswer(call -> {
            assertEquals(2L, com.careerpilot.security.CurrentUser.id());
            return new ResumeProfile(2L, "User B", "软件工程", "Java实习", List.of("Java"), "B的简历");
        });
        assertEquals("已读取简历", fixture.service.reply("same-test", "根据我的技术栈分析一下我适合什么岗位。"));
    }

    private static Fixture fixture() {
        var database = TestConversations.create();
        ChatMemory memory = database.memory();
        UserProfileService profileService = mock(UserProfileService.class);
        when(profileService.getResume()).thenReturn(new ResumeProfile(1L, "林同学", "软件工程",
                "Java后端开发实习", List.of("Java", "Spring Boot", "MySQL", "Git", "AI辅助开发"), "求职简介"));

        ChatModel model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> answer(invocation.getArgument(0)));
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        ChatService service = new ChatService(ChatClient.builder(model),
                new ResumeTools(profileService),
                new JobAnalysisTools(mock(JobService.class)),
                new GreetingTools(mock(JobService.class)),
                new JobPersistenceTools(mock(JobPersistenceService.class)),
                new ApplicationTools(mock(ApplicationService.class)),
                memory, QuestionAnswerAdvisor.builder(vectorStore).build(), null, database.service());
        return new Fixture(service, memory, profileService);
    }

    private static ChatResponse answer(Prompt prompt) {
        String currentMessage = prompt.getUserMessage().getText();
        if (currentMessage.contains("技术栈")) {
            boolean toolReturned = prompt.getInstructions().stream()
                    .anyMatch(message -> message instanceof ToolResponseMessage);
            if (toolReturned) {
                return response(new AssistantMessage("已读取简历"));
            }
            AssistantMessage toolCall = AssistantMessage.builder()
                    .toolCalls(List.of(new AssistantMessage.ToolCall("resume-call", "function", "getResume", "{}")))
                    .build();
            return response(toolCall);
        }
        if (currentMessage.contains("刚才想找什么城市")) {
            boolean mentionsHangzhou = prompt.getUserMessages().stream()
                    .anyMatch(message -> message.getText().contains("杭州"));
            return response(new AssistantMessage(mentionsHangzhou ? "杭州" : "没有提到城市"));
        }
        return response(new AssistantMessage("已记录"));
    }

    private static ChatResponse response(AssistantMessage message) {
        return new ChatResponse(List.of(new Generation(message)));
    }

    private record Fixture(ChatService service, ChatMemory memory, UserProfileService profileService) {
    }
}
